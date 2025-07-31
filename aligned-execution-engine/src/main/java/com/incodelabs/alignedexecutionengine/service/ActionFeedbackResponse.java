package com.incodelabs.alignedexecutionengine.service;

import com.incodelabs.alignedexecutionengine.integration.dto.ActionPlan;
import com.incodelabs.alignedexecutionengine.integration.dto.CheckOutputIn;
import com.incodelabs.alignedexecutionengine.integration.dto.CheckOutputRequest;
import com.incodelabs.alignedexecutionengine.integration.dto.DecisionOut;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionFeedbackResponse {
    private String prompt;
    private DecisionOut inputPromptFeedback;
    private CheckOutputIn actionPlan;
    private CheckOutputRequest checkOutputRequest;
    private DecisionOut outputFeedback;
    private String finalResult;
    @Builder.Default
    private List<ActionPlan> executionSteps = new ArrayList<>();
    @Builder.Default
    private List<String> toolExecutions = new ArrayList<>();
    @Builder.Default
    private Map<String, String> toolExecutionResults = new HashMap<>();
    private boolean completed;
    private String errorMessage;
    private int loopIteration;
    private String token;
}
