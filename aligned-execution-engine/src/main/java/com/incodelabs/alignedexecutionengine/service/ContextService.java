package com.incodelabs.alignedexecutionengine.service;

import org.springframework.stereotype.Service;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import com.incodelabs.alignedexecutionengine.service.ActionPlanService;
import com.incodelabs.alignedexecutionengine.service.PolicyCheckService;
import com.incodelabs.alignedexecutionengine.service.ToolRequestService;
import com.incodelabs.alignedexecutionengine.service.FeedbackService;
import com.incodelabs.alignedexecutionengine.integration.dto.SessionContext;
import com.incodelabs.alignedexecutionengine.integration.dto.LoopContext;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.incodelabs.alignedexecutionengine.integration.dto.ActionPlan;


@Service
@Slf4j
@RequiredArgsConstructor
public class ContextService {
    private final ActionPlanService actionPlanService;
    private final PolicyCheckService policyCheckService;
    private final ToolRequestService toolRequestService;
    private final QuerySessionService querySessionService;
    private final FeedbackService feedbackService;


    public SessionContext buildSessionContext(String sessionId) {
        log.info("Building context for session: {}", sessionId);
        
        String originalPrompt = (String) querySessionService.getQuerySession(sessionId).get("prompt");

        SessionContext context = SessionContext.builder()
            .sessionId(sessionId)
            .prompt(originalPrompt)
            .build();
        
        // Get all action plans for the session
        List<Map<String, Object>> actionPlans = actionPlanService.getActionPlansBySession(sessionId);
        context.setActionPlans(actionPlans);
        
        // Build loop history
        List<LoopContext> loops = new ArrayList<>();
        for (Map<String, Object> actionPlan : actionPlans) {

            Object actionIdObj = actionPlan.get("action_id");

            if (actionIdObj == null) {
                log.warn("Action plan missing action_id: {}", actionPlan);
                continue;
            }

            String actionId = actionIdObj.toString();
            
            LoopContext loop = buildLoopContext(actionId, actionPlan);
            loops.add(loop);
        }
        context.setLoops(loops);
        
        return context;
    }
    
    private LoopContext buildLoopContext(String actionId, Map<String, Object> actionPlan) {
        LoopContext loop = LoopContext.builder()
            .actionId(actionId)
            .actionPlan((String) actionPlan.get("plan"))
            .loopCount((Integer) actionPlan.get("loop_count"))
            .build();
        
        // Get policy checks for this action
        List<Map<String, Object>> policyChecks = policyCheckService.getPolicyChecksByAction(actionId);
        loop.setPolicyChecks(policyChecks);
        
        // Get tool requests for this action
        List<Map<String, Object>> toolRequests = toolRequestService.getToolRequestsByActionPlan(actionId);
        loop.setToolRequests(toolRequests);
        
        // Get feedback for this action
        List<Map<String, Object>> feedbackRequests = feedbackService.getFeedbackByAction(actionId);
        loop.setFeedbackRequests(feedbackRequests);
        
        return loop;
    }
    
    public String buildResumePrompt(String sessionId, String userFeedback) {
        SessionContext context = buildSessionContext(sessionId);

        String resumePrompt = "";
        resumePrompt += "Initial prompt: " + context.getPrompt() + "\n\n";
        
        // Add session history
        resumePrompt += "Session History:\n";
        resumePrompt += "Total loops: " + context.getLoops().size() + "\n\n";
        
        // Add each loop's context
        for (int i = 0; i < context.getLoops().size(); i++) {
            LoopContext loop = context.getLoops().get(i);
            resumePrompt += (context.getLoops().size() - i) + " ---\n";
            resumePrompt += "Action Plan: " + loop.getActionPlan() + "\n";
            
            // Add policy decisions
            if (loop.getPolicyChecks() != null) {
                for (Map<String, Object> policyCheck : loop.getPolicyChecks()) {
                    String decision = (String) policyCheck.get("decision");
                    String policyTriggered = (String) policyCheck.get("policy_triggered");
                    resumePrompt += "Policy Decision: " + decision + " (" + policyTriggered + ")\n";
                }
            }
            
            // Add tool executions
            if (loop.getToolRequests() != null) {
                for (Map<String, Object> toolRequest : loop.getToolRequests()) {
                    String toolName = (String) toolRequest.get("tool_name");
                    String status = (String) toolRequest.get("status");
                    String responseData = (String) toolRequest.get("response_data");
                    resumePrompt += "Tool: " + toolName + " - " + status;
                    if (responseData != null && !responseData.isEmpty()) {
                        resumePrompt += " - " + responseData;
                    }
                    resumePrompt += "\n";
                }
            }
            
            // Add feedback requests
            if (loop.getFeedbackRequests() != null) {
                for (Map<String, Object> feedbackRequest : loop.getFeedbackRequests()) {
                    String feedback = (String) feedbackRequest.get("feedback");
                    String decision = (String) feedbackRequest.get("decision");
                    resumePrompt += "Feedback: " + feedback + " (" + decision + ")\n";
                }
            }
            resumePrompt += "\n";
        }
        
        resumePrompt += "User feedback for resume: " + userFeedback + "\n";
        
        return resumePrompt.toString();
    }

public String buildPolicyPromptWithContext(String sessionId, String currentPrompt, List<ActionPlan> currentActions, String currentActionId) {
    log.info("Building context for session: {} excluding action: {}", sessionId, currentActionId);
    
    String originalPrompt = (String) querySessionService.getQuerySession(sessionId).get("prompt");
    
    StringBuilder policyPrompt = new StringBuilder();
    policyPrompt.append("=== SESSION CONTEXT ===\n");
    policyPrompt.append("Session ID: ").append(sessionId).append("\n");
    policyPrompt.append("Original User Request: ").append(originalPrompt).append("\n\n");
    
    // Get all action plans for the session EXCEPT the current one
    List<Map<String, Object>> actionPlans = actionPlanService.getActionPlansBySession(sessionId);
    List<Map<String, Object>> previousActionPlans = actionPlans.stream()
        .filter(actionPlan -> !currentActionId.equals(actionPlan.get("action_id")))
        .collect(Collectors.toList());
    
    // Add session history (excluding current loop)
    policyPrompt.append("=== SESSION HISTORY ===\n");
    policyPrompt.append("Total previous execution loops: ").append(previousActionPlans.size()).append("\n\n");
    
    // Add each previous loop's context
    for (int i = 0; i < previousActionPlans.size(); i++) {
        Map<String, Object> actionPlan = previousActionPlans.get(i);
        String actionId = actionPlan.get("action_id").toString();
        
        policyPrompt.append("--- Loop ").append(previousActionPlans.size() - i).append(" ---\n");
        policyPrompt.append("Action Plan: ").append(actionPlan.get("plan")).append("\n");
        
        // Get policy checks for this action
        List<Map<String, Object>> policyChecks = policyCheckService.getPolicyChecksByAction(actionId);
        if (policyChecks != null && !policyChecks.isEmpty()) {
            policyPrompt.append("Policy Decisions:\n");
            for (Map<String, Object> policyCheck : policyChecks) {
                String decision = (String) policyCheck.get("decision");
                String policyTriggered = (String) policyCheck.get("policy_triggered");
                String reason = (String) policyCheck.get("reason");
                policyPrompt.append("  - Decision: ").append(decision)
                           .append(", Policy: ").append(policyTriggered)
                           .append(", Reason: ").append(reason).append("\n");
            }
        }
        
        // Get tool requests for this action
        List<Map<String, Object>> toolRequests = toolRequestService.getToolRequestsByActionPlan(actionId);
        if (toolRequests != null && !toolRequests.isEmpty()) {
            policyPrompt.append("Tool Executions:\n");
            for (Map<String, Object> toolRequest : toolRequests) {
                String toolName = (String) toolRequest.get("tool_name");
                String status = (String) toolRequest.get("tool_status");
                String responseData = (String) toolRequest.get("response_data");
                policyPrompt.append("  - Tool: ").append(toolName)
                           .append(", Status: ").append(status);
                if (responseData != null && !responseData.isEmpty()) {
                    policyPrompt.append(", Result: ").append(responseData);
                }
                policyPrompt.append("\n");
            }
        }
        
        // Get feedback for this action
        List<Map<String, Object>> feedbackRequests = feedbackService.getFeedbackByAction(actionId);
        if (feedbackRequests != null && !feedbackRequests.isEmpty()) {
            policyPrompt.append("Feedback Requests:\n");
            for (Map<String, Object> feedbackRequest : feedbackRequests) {
                String feedback = (String) feedbackRequest.get("feedback");
                String decision = (String) feedbackRequest.get("decision");
                policyPrompt.append("  - Feedback: ").append(feedback)
                           .append(", Decision: ").append(decision).append("\n");
            }
        }
        policyPrompt.append("\n");
    }
    
    // Add current request
    policyPrompt.append("=== CURRENT REQUEST ===\n");
    policyPrompt.append("Current Prompt: ").append(currentPrompt).append("\n");
    policyPrompt.append("Current Action Plan: ").append(currentActions != null ? 
        currentActions.stream().map(ActionPlan::getTool).collect(Collectors.joining(", ")) : "None").append("\n");
    
    if (currentActions != null && !currentActions.isEmpty()) {
        policyPrompt.append("Planned Tool Executions:\n");
        for (ActionPlan action : currentActions) {
            policyPrompt.append("  - Tool: ").append(action.getTool());
            if (action.getParameters() != null && !action.getParameters().isEmpty()) {
                policyPrompt.append(", Parameters: ").append(action.getParameters());
            }
            policyPrompt.append("\n");
        }
    }
    
    String fullPrompt = policyPrompt.toString();
    
    // Limit to 500 words
    String[] words = fullPrompt.split("\\s+");
    if (words.length > 500) {
        StringBuilder truncatedPrompt = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            truncatedPrompt.append(words[i]).append(" ");
        }
        truncatedPrompt.append("... [truncated at 500 words]");
        return truncatedPrompt.toString().trim();
    }
    
    return fullPrompt;
}

// Add a new method that excludes the current action
public SessionContext buildSessionContextExcludingCurrent(String sessionId, String currentActionId) {
    log.info("Building context for session: {} excluding action: {}", sessionId, currentActionId);
    
    String originalPrompt = (String) querySessionService.getQuerySession(sessionId).get("prompt");

    SessionContext context = SessionContext.builder()
        .sessionId(sessionId)
        .prompt(originalPrompt)
        .build();
    
    // Get all action plans for the session EXCEPT the current one
    List<Map<String, Object>> actionPlans = actionPlanService.getActionPlansBySession(sessionId);
    List<Map<String, Object>> filteredActionPlans = actionPlans.stream()
        .filter(actionPlan -> !currentActionId.equals(actionPlan.get("action_id")))
        .collect(Collectors.toList());
    
    context.setActionPlans(filteredActionPlans);
    
    // Build loop history (excluding current)
    List<LoopContext> loops = new ArrayList<>();
    for (Map<String, Object> actionPlan : filteredActionPlans) {
        Object actionIdObj = actionPlan.get("action_id");
        if (actionIdObj == null) {
            log.warn("Action plan missing action_id: {}", actionPlan);
            continue;
        }
        String actionId = actionIdObj.toString();
        LoopContext loop = buildLoopContext(actionId, actionPlan);
        loops.add(loop);
    }
    context.setLoops(loops);
    
    return context;
}

public String buildContextSummary(String sessionId) {
    SessionContext context = buildSessionContext(sessionId);
    // need to append all context loops to the prompt
    String contextSummary = "";
    contextSummary += "Context summary: ";
    for (LoopContext loop : context.getLoops()) {
        contextSummary += loop.getActionPlan();
        List<Map<String, Object>> policyChecks = loop.getPolicyChecks();
        if (policyChecks != null && !policyChecks.isEmpty()) {
            contextSummary += "Policy checks: ";
            for (Map<String, Object> policyCheck : policyChecks) {
                contextSummary += policyCheck.get("decision");
            }
        }
        List<Map<String, Object>> toolRequests = loop.getToolRequests();
        if (toolRequests != null && !toolRequests.isEmpty()) {
            contextSummary += "Tool requests: ";
            for (Map<String, Object> toolRequest : toolRequests) {
                contextSummary += toolRequest.get("tool_name");
            }
        }
    }
    log.info("Context summary: {}", contextSummary);
    if (contextSummary.length() > 1000) {
        contextSummary = contextSummary.substring(0, 1000);
    }
    return contextSummary;
}
}