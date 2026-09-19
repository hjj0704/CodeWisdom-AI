package com.codewisdom.evaluation.service;

import com.codewisdom.evaluation.config.WorkspaceProperties;
import com.codewisdom.evaluation.dto.SandboxDtos.DemoLinkView;
import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 从项目 README / 文档中提取「在线演示」类外链。
 */
@Service
public class DemoLinkDiscoverService {

    private static final int MIN_SCORE = 5;
    private static final int MAX_DOC_FILES = 24;
    private static final int MAX_DOC_BYTES = 120_000;
    private static final int MAX_REMOTE_BYTES = 96_000;

    private static final List<String> DOC_CANDIDATES = List.of(
            "README.md", "README", "readme.md", "Readme.md",
            "docs/README.md", "docs/demo.md", "docs/DEMO.md", "docs/index.md",
            "index.html", "package.json");

    private static final Pattern MARKDOWN_LINK = Pattern.compile(
            "\\[([^\\]]*)]\\((https?://[^)\\s]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_URL = Pattern.compile(
            "(https?://[^\\s<>\"'\\]\\)]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOMEPAGE = Pattern.compile(
            "\"homepage\"\\s*:\\s*\"(https?://[^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private static final List<String> POSITIVE_KEYWORDS = List.of(
            "demo", "live", "preview", "playground", "在线演示", "在线预览",
            "演示地址", "体验地址", "线上演示", "访问演示", "预览地址", "try it", "homepage");

    private static final List<String> HOST_BONUS = List.of(
            "github.io", "gitee.io", "vercel.app", "netlify.app", "pages.dev",
            "surge.sh", "herokuapp.com", "web.app", "firebaseapp.com");

    private final WorkspaceProperties workspaceProperties;

    public DemoLinkDiscoverService(WorkspaceProperties workspaceProperties) {
        this.workspaceProperties = workspaceProperties;
    }

    public DemoLinkView discover(long projectId, String sourceUrl) {
        Path scanRoot = scanRoot(projectId);
        if (!Files.isDirectory(scanRoot)) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目工作区不存在: " + projectId);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (String docPath : collectDocPaths(scanRoot)) {
            readDoc(scanRoot.resolve(docPath)).ifPresent(content ->
                    candidates.addAll(extractFromContent(content, docPath, sourceUrl)));
        }

        Optional<Candidate> best = pickBest(candidates);
        if (best.isEmpty() && sourceUrl != null && !sourceUrl.isBlank()) {
            fetchRemoteReadme(sourceUrl).ifPresent(content ->
                    candidates.addAll(extractFromContent(content, "远程 README", sourceUrl)));
            best = pickBest(candidates);
        }

        return best.map(c -> new DemoLinkView(
                        true,
                        c.url(),
                        c.label(),
                        c.source(),
                        "在 " + c.source() + " 中发现演示链接"))
                .orElse(new DemoLinkView(
                        false,
                        null,
                        null,
                        null,
                        "未在 README/文档中发现在线演示链接"));
    }

    static List<Candidate> extractFromContent(String content, String sourceFile) {
        return extractFromContent(content, sourceFile, null);
    }

    static List<Candidate> extractFromContent(String content, String sourceFile, String sourceUrl) {
        List<Candidate> found = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return found;
        }

        if ("package.json".equals(sourceFile)) {
            Matcher homepage = HOMEPAGE.matcher(content);
            if (homepage.find()) {
                String url = normalizeUrl(homepage.group(1));
                if (isAllowedDemoUrl(url, sourceUrl)) {
                    found.add(new Candidate(url, "Homepage", sourceFile, scoreLink("homepage", "", url, sourceUrl) + 4));
                }
            }
        }

        String[] lines = content.split("\n");
        for (String line : lines) {
            Matcher md = MARKDOWN_LINK.matcher(line);
            while (md.find()) {
                String label = md.group(1).trim();
                String url = normalizeUrl(md.group(2));
                if (!isAllowedDemoUrl(url, sourceUrl)) {
                    continue;
                }
                int score = scoreLink(label, line, url, sourceUrl);
                if (score >= MIN_SCORE) {
                    found.add(new Candidate(url, label.isBlank() ? url : label, sourceFile, score));
                }
            }
            if (line.toLowerCase(Locale.ROOT).contains("demo")
                    || line.contains("演示")
                    || line.toLowerCase(Locale.ROOT).contains("live")) {
                Matcher plain = PLAIN_URL.matcher(line);
                while (plain.find()) {
                    String url = normalizeUrl(plain.group(1));
                    if (!isAllowedDemoUrl(url, sourceUrl)) {
                        continue;
                    }
                    int score = scoreLink("", line, url, sourceUrl);
                    if (score >= MIN_SCORE) {
                        found.add(new Candidate(url, url, sourceFile, score));
                    }
                }
            }
        }
        return found;
    }

    private static Optional<Candidate> pickBest(List<Candidate> candidates) {
        return candidates.stream()
                .filter(c -> c.score() >= MIN_SCORE)
                .max(Comparator.comparingInt(Candidate::score)
                        .thenComparing(c -> c.label().length()));
    }

    private static int scoreLink(String label, String line, String url, String sourceUrl) {
        int score = 0;
        String combined = (label + " " + line + " " + url).toLowerCase(Locale.ROOT);
        for (String keyword : POSITIVE_KEYWORDS) {
            if (combined.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 3;
            }
        }
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        for (String host : HOST_BONUS) {
            if (lowerUrl.contains(host)) {
                score += 6;
            }
        }
        if (label.matches("(?i).*(demo|演示|预览|live|体验).*")) {
            score += 8;
        }
        if (isSameRepoUrl(url, sourceUrl)) {
            score -= 20;
        }
        if (lowerUrl.matches("https?://(www\\.)?github\\.com/[^/]+/[^/]+/?$")) {
            score -= 12;
        }
        if (lowerUrl.contains("raw.githubusercontent.com") || lowerUrl.contains("/raw/")) {
            score -= 8;
        }
        if (lowerUrl.contains("gitee.com") && !lowerUrl.contains("gitee.io")) {
            score -= 4;
        }
        if (lowerUrl.contains("localhost") || lowerUrl.contains("127.0.0.1")) {
            score -= 15;
        }
        return score;
    }

    private static boolean isAllowedDemoUrl(String url, String sourceUrl) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:")) {
            return false;
        }
        return !isSameRepoUrl(url, sourceUrl);
    }

    private static boolean isSameRepoUrl(String url, String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return false;
        }
        String a = normalizeRepoKey(url);
        String b = normalizeRepoKey(sourceUrl);
        return !a.isBlank() && a.equals(b);
    }

    private static String normalizeRepoKey(String url) {
        try {
            URI uri = URI.create(url.trim().replaceAll("\\.git$", ""));
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            if (host.isBlank() || path.isBlank()) {
                return "";
            }
            return host + path.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeUrl(String url) {
        return url.trim().replaceAll("[),.;]+$", "");
    }

    private List<String> collectDocPaths(Path scanRoot) {
        Set<String> paths = new LinkedHashSet<>(DOC_CANDIDATES);
        Path docsDir = scanRoot.resolve("docs");
        if (Files.isDirectory(docsDir)) {
            try (Stream<Path> walk = Files.walk(docsDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".md") || name.endsWith(".html");
                        })
                        .map(p -> scanRoot.relativize(p).toString().replace('\\', '/'))
                        .limit(MAX_DOC_FILES)
                        .forEach(paths::add);
            } catch (IOException ignored) {
                // skip
            }
        }
        List<String> existing = new ArrayList<>();
        for (String path : paths) {
            if (Files.isRegularFile(scanRoot.resolve(path))) {
                existing.add(path);
            }
            if (existing.size() >= MAX_DOC_FILES) {
                break;
            }
        }
        return existing;
    }

    private Optional<String> readDoc(Path file) {
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_DOC_BYTES) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<String> fetchRemoteReadme(String sourceUrl) {
        try {
            URI repo = URI.create(sourceUrl.trim().replaceAll("\\.git$", ""));
            String host = repo.getHost() == null ? "" : repo.getHost().toLowerCase(Locale.ROOT);
            String[] segments = repo.getPath().split("/");
            List<String> parts = new ArrayList<>();
            for (String segment : segments) {
                if (!segment.isBlank()) {
                    parts.add(segment.replace(".git", ""));
                }
            }
            if (parts.size() < 2) {
                return Optional.empty();
            }
            String owner = parts.get(0);
            String repoName = parts.get(1);

            List<String> candidates = new ArrayList<>();
            if (host.contains("github.com")) {
                candidates.add("https://raw.githubusercontent.com/" + owner + "/" + repoName + "/main/README.md");
                candidates.add("https://raw.githubusercontent.com/" + owner + "/" + repoName + "/master/README.md");
            } else if (host.contains("gitee.com")) {
                candidates.add("https://gitee.com/" + owner + "/" + repoName + "/raw/main/README.md");
                candidates.add("https://gitee.com/" + owner + "/" + repoName + "/raw/master/README.md");
            } else if (host.contains("gitlab.com")) {
                candidates.add("https://gitlab.com/" + owner + "/" + repoName + "/-/raw/main/README.md");
                candidates.add("https://gitlab.com/" + owner + "/" + repoName + "/-/raw/master/README.md");
            }
            for (String url : candidates) {
                Optional<String> body = httpGetText(url);
                if (body.isPresent() && !body.get().isBlank()) {
                    return body;
                }
            }
        } catch (Exception ignored) {
            // skip remote fetch
        }
        return Optional.empty();
    }

    private static Optional<String> httpGetText(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "CodeWisdom-DemoLink/1.0");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return Optional.empty();
            }
            try (InputStream in = conn.getInputStream()) {
                byte[] bytes = in.readNBytes(MAX_REMOTE_BYTES);
                return Optional.of(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            return Optional.empty();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Path scanRoot(long projectId) {
        Path workspace = Paths.get(workspaceProperties.getRoot()).resolve(String.valueOf(projectId));
        Path repoDir = workspace.resolve("repo");
        if (Files.isDirectory(repoDir)) {
            return repoDir;
        }
        return workspace;
    }

    record Candidate(String url, String label, String source, int score) {
    }
}
