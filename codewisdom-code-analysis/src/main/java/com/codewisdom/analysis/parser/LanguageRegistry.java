package com.codewisdom.analysis.parser;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.treesitter.TSLanguage;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterPython;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 语言注册表：语言标识 → Tree-Sitter grammar。
 *
 * <p>grammar 实例<b>缓存且永不释放</b>。Tree-Sitter 的 {@link TSLanguage} 是不可变的
 * 只读结构，可以跨线程共享与复用；而它背后的原生指针一旦 {@code close()} 就失效，
 * 缓存对象被别处继续引用会直接崩 JVM。因此这里刻意不做 close——
 * 生命周期与 JVM 一致，代价是每个 grammar 常驻几 MB。
 *
 * <p>要新增语言，在 {@link #SUPPLIERS} 里加一行，并在 {@code pom.xml} 引入对应 grammar 包
 * （{@code io.github.bonede:tree-sitter-<lang>}）。<b>不要</b>在别处直接 new grammar，
 * 否则会绕开缓存与线程安全约定。
 */
@Component
public class LanguageRegistry {

    private static final Logger log = LoggerFactory.getLogger(LanguageRegistry.class);

    /**
     * 支持的语言。key 与 {@code LanguageDetector} 输出的语言标识保持一致，
     * 这样文件树里的 {@code language} 字段可以直接拿来用。
     */
    private static final Map<String, Supplier<TSLanguage>> SUPPLIERS = new LinkedHashMap<>();

    static {
        SUPPLIERS.put("java", TreeSitterJava::new);
        SUPPLIERS.put("python", TreeSitterPython::new);
    }

    /** 已加载的 grammar 缓存；用 synchronized 保证并发下只创建一次。 */
    private final Map<String, TSLanguage> cache = new LinkedHashMap<>();

    /**
     * 获取 grammar。
     *
     * @param languageId 语言标识，如 {@code java}
     * @throws BizException 语言不支持
     */
    public TSLanguage get(String languageId) {
        if (languageId == null || languageId.isBlank()) {
            throw BizException.of(ErrorCode.UNSUPPORTED_LANGUAGE, "语言标识不能为空");
        }
        String key = languageId.trim().toLowerCase(java.util.Locale.ROOT);

        Supplier<TSLanguage> supplier = SUPPLIERS.get(key);
        if (supplier == null) {
            throw BizException.of(ErrorCode.UNSUPPORTED_LANGUAGE,
                    "暂不支持的语言: " + languageId + "，当前支持: " + SUPPLIERS.keySet());
        }

        synchronized (cache) {
            return cache.computeIfAbsent(key, k -> {
                TSLanguage language = supplier.get();
                log.info("加载 grammar language={} abiVersion={}", k, language.abiVersion());
                return language;
            });
        }
    }

    /** 是否支持该语言。 */
    public boolean supports(String languageId) {
        return languageId != null && SUPPLIERS.containsKey(languageId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** 当前支持的全部语言标识。 */
    public Set<String> supportedLanguages() {
        return Set.copyOf(SUPPLIERS.keySet());
    }
}
