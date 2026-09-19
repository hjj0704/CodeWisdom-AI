package com.codewisdom.agent.service.doc;

import com.codewisdom.agent.domain.doc.DocGenModels.DocGenResult;
import com.codewisdom.agent.domain.doc.DocGenModels.MethodDocRequest;
import com.codewisdom.agent.domain.doc.DocGenModels.ParameterDocRequest;
import com.codewisdom.agent.domain.doc.DocGenModels.TypeDocRequest;
import com.codewisdom.agent.dto.DocGenDtos.DocGenResponseView;
import com.codewisdom.agent.dto.DocGenDtos.MethodDocGenRequest;
import com.codewisdom.agent.dto.DocGenDtos.TypeDocGenRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class DocGenFacade {

    private final DocCommentGenerator docCommentGenerator;

    public DocGenFacade(DocCommentGenerator docCommentGenerator) {
        this.docCommentGenerator = Objects.requireNonNull(docCommentGenerator);
    }

    public DocGenResponseView generate(TypeDocGenRequest request) {
        TypeDocRequest domain = toDomain(request);
        DocGenResult result = docCommentGenerator.generate(domain);
        if (result.skipped()) {
            return new DocGenResponseView(
                    true,
                    "",
                    result.methodComments(),
                    "文档注释生成已关闭（服务端 codewisdom.doc-gen.enabled=false）");
        }
        return new DocGenResponseView(
                false,
                result.typeComment(),
                result.methodComments(),
                "生成成功");
    }

    private static TypeDocRequest toDomain(TypeDocGenRequest request) {
        List<MethodDocRequest> methods = request.methods().stream()
                .map(DocGenFacade::toMethod)
                .toList();
        return new TypeDocRequest(
                request.qualifiedName(),
                request.kind(),
                request.classCommentHint(),
                methods);
    }

    private static MethodDocRequest toMethod(MethodDocGenRequest request) {
        List<ParameterDocRequest> params = request.parameters().stream()
                .map(p -> new ParameterDocRequest(p.name(), p.type()))
                .toList();
        return new MethodDocRequest(
                request.name(),
                request.returnType(),
                request.constructor(),
                params);
    }
}
