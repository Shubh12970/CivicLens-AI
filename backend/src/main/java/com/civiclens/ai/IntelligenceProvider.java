package com.civiclens.ai;

import com.civiclens.ai.AnalysisModels.AnalysisInput;
import com.civiclens.ai.AnalysisModels.ProviderResult;

/**
 * Boundary to the LLM. Implementations only return suggestions; they have no access to repositories or workflow
 * services, so model output can never directly change state.
 */
public interface IntelligenceProvider {

    String name();

    String model();

    boolean isConfigured();

    ProviderResult analyze(AnalysisInput input) throws ProviderException;
}
