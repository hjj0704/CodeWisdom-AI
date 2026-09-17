package com.codewisdom.analysis.dto;

public record ArchitectureView(
        String layerDiagram,
        String packageDiagram,
        int typeCount,
        int callEdgeCount
) {
}
