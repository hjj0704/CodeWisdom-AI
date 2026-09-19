package com.codewisdom.agent.controller;

import com.codewisdom.agent.dto.DocGenDtos.DocGenResponseView;
import com.codewisdom.agent.dto.DocGenDtos.TypeDocGenRequest;
import com.codewisdom.agent.service.doc.DocGenFacade;
import com.codewisdom.common.api.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocController {

    private final DocGenFacade docGenFacade;

    public DocController(DocGenFacade docGenFacade) {
        this.docGenFacade = docGenFacade;
    }

    @PostMapping("/projects/{projectId}/doc/javadoc")
    public R<DocGenResponseView> generateJavadoc(@PathVariable long projectId,
                                               @Valid @RequestBody TypeDocGenRequest request) {
        return R.ok(docGenFacade.generate(request));
    }
}
