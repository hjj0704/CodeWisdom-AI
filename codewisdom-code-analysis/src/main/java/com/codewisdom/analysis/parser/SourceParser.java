package com.codewisdom.analysis.parser;

import com.codewisdom.common.api.ErrorCode;
import com.codewisdom.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.treesitter.TSLanguage;
import org.treesitter.TSParser;
import org.treesitter.TSTree;

/**
 * 源码解析入口。
 *
 * <p><b>每次调用新建 {@link TSParser}</b>：该对象持有解析状态，不是线程安全的，
 * 共享会导致并发解析互相污染。{@code ts_parser_new()} 只是分配一个轻量结构，
 * 相对文件 IO 与语法树构建可以忽略。若后续压测发现是瓶颈，再改为
 * {@code ThreadLocal} 池化——但那是优化，不是现在的正确性前提。
 *
 * <p>grammar 则相反，来自 {@link LanguageRegistry} 的共享缓存（只读结构，可安全共享）。
 */
@Component
public class SourceParser {

    private static final Logger log = LoggerFactory.getLogger(SourceParser.class);

    /** 单文件解析上限，防止误传超大文件把内存打满。 */
    private static final int MAX_SOURCE_CHARS = 4 * 1024 * 1024;

    private final LanguageRegistry languageRegistry;

    public SourceParser(LanguageRegistry languageRegistry) {
        this.languageRegistry = languageRegistry;
    }

    /**
     * 解析源码。
     *
     * <p>即使源码存在语法错误也会返回句柄（Tree-Sitter 容错解析），
     * 通过 {@link ParseHandle#hasError()} 判断。这一点很重要：真实项目里
     * 常常混有无法编译的文件，因语法错误直接放弃解析会漏掉大量有效结构。
     *
     * @param languageId 语言标识，如 {@code java}
     * @param source     源码文本
     * @return 解析句柄，<b>必须关闭</b>
     * @throws BizException 语言不支持或源码超限
     */
    public ParseHandle parse(String languageId, String source) {
        if (source == null) {
            throw BizException.of(ErrorCode.PARSE_ERROR, "源码内容为空");
        }
        if (source.length() > MAX_SOURCE_CHARS) {
            throw BizException.of(ErrorCode.PARSE_ERROR,
                    "源码超过解析上限 " + MAX_SOURCE_CHARS + " 字符");
        }

        TSLanguage language = languageRegistry.get(languageId);

        TSParser parser = new TSParser();
        try {
            if (!parser.setLanguage(language)) {
                throw BizException.of(ErrorCode.PARSE_ERROR,
                        "加载 grammar 失败，可能是 ABI 不兼容: " + languageId);
            }
            TSTree tree = parser.parseString(null, source);
            if (tree == null) {
                throw BizException.of(ErrorCode.PARSE_ERROR, "解析未返回语法树");
            }
            return new ParseHandle(languageId, source, tree);
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("解析失败 language={} message={}", languageId, e.getMessage());
            throw BizException.of(ErrorCode.PARSE_ERROR, "解析失败: " + e.getMessage(), e);
        } finally {
            // parser 用完即弃；注意语法树由 ParseHandle 持有，不随 parser 释放
            parser.close();
        }
    }

    /** 该语言是否可解析。 */
    public boolean supports(String languageId) {
        return languageRegistry.supports(languageId);
    }
}
