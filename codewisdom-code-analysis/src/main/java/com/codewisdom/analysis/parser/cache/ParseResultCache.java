package com.codewisdom.analysis.parser.cache;

import com.codewisdom.analysis.parser.SourceStructure;

import java.util.Optional;

public interface ParseResultCache {

    Optional<SourceStructure> get(String cacheKey);

    void put(String cacheKey, SourceStructure structure);
}
