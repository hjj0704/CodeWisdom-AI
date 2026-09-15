package com.codewisdom.analysis.parser;

import org.treesitter.TSNode;
import org.treesitter.TSTree;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 一次解析的句柄，持有语法树与源码字节。
 *
 * <p>必须关闭（{@code try}-with-resources），否则原生语法树不会释放。
 *
 * <p><b>文本提取只走 {@link #text(TSNode)}</b>：Tree-Sitter 返回的是 UTF-8 字节偏移
 * （{@code getStartByte/getEndByte}），而 {@link String#substring} 用的是 UTF-16 字符索引。
 * 源码一旦含中文等多字节字符，直接 substring 会切出乱码。把这条规则收敛到一个方法里，
 * 是为了让调用方没有机会写错。
 */
public final class ParseHandle implements AutoCloseable {

    private final String languageId;
    private final byte[] sourceBytes;
    private final TSTree tree;

    ParseHandle(String languageId, String source, TSTree tree) {
        this.languageId = languageId;
        this.sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        this.tree = tree;
    }

    public String languageId() {
        return languageId;
    }

    /** 源码字节（UTF-8）。 */
    public byte[] sourceBytes() {
        return sourceBytes;
    }

    /** 源码文本。 */
    public String source() {
        return new String(sourceBytes, StandardCharsets.UTF_8);
    }

    /** 根节点，节点类型通常为 {@code program}（Java / Python 皆是）。 */
    public TSNode root() {
        return tree.getRootNode();
    }

    /** 源码是否存在语法错误。 */
    public boolean hasError() {
        return root().hasError();
    }

    /**
     * 取节点对应的源码文本。
     *
     * <p>按 UTF-8 字节切片再解码——见类注释。
     */
    public String text(TSNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        int start = node.getStartByte();
        int end = node.getEndByte();
        if (start < 0 || end > sourceBytes.length || start > end) {
            return "";
        }
        return new String(sourceBytes, start, end - start, StandardCharsets.UTF_8);
    }

    /** 节点起始行号（<b>1-based</b>，便于直接展示给用户；Tree-Sitter 原生是 0-based）。 */
    public int startLine(TSNode node) {
        return node.getStartPoint().getRow() + 1;
    }

    /** 节点结束行号（1-based）。 */
    public int endLine(TSNode node) {
        return node.getEndPoint().getRow() + 1;
    }

    /**
     * 取节点 {@code name} 字段的文本。声明类节点（类/方法/字段）都有该字段。
     *
     * @return 名称；无 name 字段时返回 {@code null}
     */
    public String nameOf(TSNode node) {
        TSNode name = node.getChildByFieldName("name");
        if (name == null || name.isNull()) {
            return null;
        }
        return text(name);
    }

    /** 深度优先遍历全部节点。 */
    public void walk(Consumer<TSNode> visitor) {
        walk(root(), visitor);
    }

    private static void walk(TSNode node, Consumer<TSNode> visitor) {
        visitor.accept(node);
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            walk(node.getChild(i), visitor);
        }
    }

    /** 收集指定类型的所有节点，保持源码出现顺序。 */
    public List<TSNode> findAll(String nodeType) {
        List<TSNode> result = new ArrayList<>();
        walk(node -> {
            if (nodeType.equals(node.getType())) {
                result.add(node);
            }
        });
        return result;
    }

    /** 收集若干类型的所有节点。 */
    public List<TSNode> findAll(List<String> nodeTypes) {
        List<TSNode> result = new ArrayList<>();
        walk(node -> {
            if (nodeTypes.contains(node.getType())) {
                result.add(node);
            }
        });
        return result;
    }

    /** 直接子节点中指定类型的节点。 */
    public List<TSNode> findChildren(TSNode parent, String nodeType) {
        List<TSNode> result = new ArrayList<>();
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = parent.getChild(i);
            if (nodeType.equals(child.getType())) {
                result.add(child);
            }
        }
        return result;
    }

    @Override
    public void close() {
        tree.close();
    }
}
