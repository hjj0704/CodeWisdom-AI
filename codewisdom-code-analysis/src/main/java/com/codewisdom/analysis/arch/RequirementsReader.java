package com.codewisdom.analysis.arch;

import com.codewisdom.analysis.domain.PythonRequirement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code requirements.txt} 读取器。
 *
 * <p>与技术栈识别共用的无状态纯函数（理由同 {@link PomDependencyReader}：两份必须同步的解析
 * 会漂移出互相矛盾的结论；静态方法则不动调用方的构造签名）。
 *
 * <h2>支持的行形态</h2>
 * <pre>
 * 包名
 * 包名==1.2.3                   钉死版本
 * 包名&gt;=1.0,&lt;2.0             区间约束
 * 包名[extra1,extra2]==1.2.3    extras
 * 包名==1.2.3 ; python_version &lt; "3.9"   环境标记
 * </pre>
 *
 * <h2>跳过的行</h2>
 * 空行、{@code #} 注释、以及以 {@code -} 开头的选项行（{@code -r} / {@code -c} / {@code -e} /
 * {@code --index-url} / {@code -i}）。<b>不跟随 {@code -r} 包含的文件</b>——
 * 调用方把要分析的文件一次给全，包含关系由调用方决定；读取器自作主张地去读磁盘上的另一个文件，
 * 会让「同一份输入得到同一份输出」这条约定失效。
 *
 * <h2>一处刻意的严格</h2>
 * <b>单等号 {@code =} 判为无效操作符</b>：{@code django=4.2} 这种写法多半是从 {@code setup.py}
 * 里抄过来的，pip 会直接报 {@code Invalid requirement} 拒绝安装。它不是「宽松但能用」，
 * 而是<b>装不上</b>——所以必须报出来，不能当没看见。
 */
public final class RequirementsReader {

    /** 合法操作符，长的在前，避免 {@code >=} 被 {@code >} 抢先匹配。 */
    private static final List<String> OPERATORS = List.of("==", ">=", "<=", "~=", "!=", ">", "<");

    /** 行首的包名 + 其余部分。包名字符集刻意不含 {@code = > < ! ~ [ ;}，能自然停在操作符前。 */
    private static final Pattern LINE = Pattern.compile("^([A-Za-z0-9._-]+)\\s*(.*)$");

    private RequirementsReader() {
    }

    /**
     * 读取一个 requirements 文件的全部声明。
     *
     * @param path    文件路径，写进结果的 {@code filePath}
     * @param content 文件原文
     * @return 声明列表；跳过空行、注释与选项行。<b>解析不出包名的行也会返回</b>
     *         （{@code name} 为 {@code null}），由冲突检测报成无效声明
     */
    public static List<PythonRequirement> read(String path, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<PythonRequirement> requirements = new ArrayList<>();
        String[] lines = content.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            PythonRequirement requirement = parseLine(path, lines[i], i + 1);
            if (requirement != null) {
                requirements.add(requirement);
            }
        }
        return requirements;
    }

    /**
     * 解析一行。
     *
     * @return 一条声明；空行 / 注释 / 选项行返回 {@code null}
     */
    private static PythonRequirement parseLine(String path, String rawLine, int lineNumber) {
        String line = stripComment(rawLine).trim();
        if (line.isEmpty() || line.startsWith("-")) {
            return null;
        }

        String marker = null;
        int semicolon = line.indexOf(';');
        if (semicolon >= 0) {
            marker = line.substring(semicolon + 1).trim();
            line = line.substring(0, semicolon).trim();
            if (marker.isEmpty()) {
                marker = null;
            }
        }

        Matcher matcher = LINE.matcher(line);
        if (!matcher.matches()) {
            return new PythonRequirement(null, null, marker, path, lineNumber, line);
        }

        String name = PythonRequirement.normalizeName(matcher.group(1));
        String rest = stripExtras(matcher.group(2).trim());
        return new PythonRequirement(name, rest.isEmpty() ? null : rest, marker, path, lineNumber, line);
    }

    /** 去掉行内注释。包名与版本约束里不会出现 {@code #}，直接截断是安全的。 */
    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    /** 去掉 extras：{@code [security,socks]==1.0} → {@code ==1.0}。 */
    private static String stripExtras(String rest) {
        if (!rest.startsWith("[")) {
            return rest;
        }
        int end = rest.indexOf(']');
        return end < 0 ? rest : rest.substring(end + 1).trim();
    }

    /**
     * 约束是否合法：要么没有约束，要么以合法操作符开头。
     *
     * @return 合法返回 {@code true}
     */
    public static boolean isValidConstraint(String constraint) {
        if (constraint == null || constraint.isBlank()) {
            return true;
        }
        String trimmed = constraint.trim();
        return OPERATORS.stream().anyMatch(trimmed::startsWith);
    }
}
