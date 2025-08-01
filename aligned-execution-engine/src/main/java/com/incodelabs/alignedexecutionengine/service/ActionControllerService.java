package com.incodelabs.alignedexecutionengine.service;

import com.incodelabs.alignedexecutionengine.integration.PolicyApiClient;
import com.incodelabs.alignedexecutionengine.integration.dto.*;
import com.incodelabs.alignedexecutionengine.integration.email.EmailClientApi;
import com.incodelabs.alignedexecutionengine.integration.email.dto.EmailRequest;
import com.incodelabs.alignedexecutionengine.integration.verification.IncodeVerificationApiClient;
import com.incodelabs.alignedexecutionengine.integration.verification.dto.StartVerificationResponse;
import com.incodelabs.alignedexecutionengine.integration.verification.dto.TokenResponse;
import com.incodelabs.alignedexecutionengine.integration.verification.dto.TokenValidationResponse;
import com.incodelabs.alignedexecutionengine.integration.verification.dto.VerificationStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActionControllerService {
    private final PromptsUtil promptsUtil;
    private final ChatClient openAiChatClient;
    private final PolicyApiClient policyApi;
    private final McpClientService mcpClientService;
    private final VerificationService verificationService;
    private final EmailClientApi emailClientApi;
    private final QuerySessionService querySessionService;
    private final ActionPlanService actionPlanService;
    private final PolicyCheckService policyCheckService;
    private final ToolRequestService toolRequestService;
    private final FeedbackService feedbackService;
    private final ContextService contextService;
    
    private static final int MAX_FEEDBACK_LOOPS = 50;
    private String currentVerificationToken; // Store token for use in tool parameters

    // Julio - added sessionID parameter so we can log with logging service
    public ActionFeedbackResponse testControllerAgent(String prompt, String sessionID, Boolean resume) {

        String processedPrompt = prompt;
        ActionFeedbackResponse feedback = ActionFeedbackResponse.builder().build();
        feedback.setPrompt(prompt);
        currentVerificationToken = verificationService.getToken(getCurrentUserEmail()).getToken();
        log.info("Initial verification token: {}", currentVerificationToken);
        // Julio
        if (!resume) {
            querySessionService.startQuerySession(prompt, sessionID);
        } 
        else {
            // Handle resume case and check if we need to get token from IDV service
            if (currentVerificationToken == null || currentVerificationToken.isEmpty()) {
                log.info("Resuming IDV session, checking for verification token");
                
                // Try to get token from IDV service
                String userEmail = getCurrentUserEmail();
                var tokenResponse = verificationService.getToken(userEmail);
                
                if (tokenResponse.isSuccess() && tokenResponse.getToken() != null) {
                    log.info("Retrieved verification token from IDV-MCP");
                    currentVerificationToken = tokenResponse.getToken();
                } else {
                    log.warn("No verification token found in IDV-MCP, user may not have completed verification");
                }
            }
        }

        try {
            if (!resume) {
                // Step 1: Validate initial prompt
                Optional<DecisionOut> promptValidation = validateClientPrompt(prompt + "Staged Tool Executions: None");
                
                if (promptValidation.isEmpty()) {
                    log.warn("Prompt validation failed: {}", prompt);
                    feedback.setErrorMessage("Prompt validation failed");
                    querySessionService.updateSessionPolicy(sessionID, "failed", "Prompt validation failed");
                    return feedback;
                }
                
                DecisionOut promptDecision = promptValidation.get();
                feedback.setInputPromptFeedback(promptDecision);
                String details = "Triggered policy: " + promptDecision.getPolicyId() + "\n Reason: " + promptDecision.getReason();
                
                if (PerPolicy.AlignmentType.deny.equals(promptDecision.getAlignment())) {
                    feedback.setCompleted(true);
                    querySessionService.updateSessionPolicy(sessionID, "deny", details);
                    return feedback;
                }
                
                // Handle IDV case
                processedPrompt = prompt;
                Boolean idvTriggered = false;
                if (PerPolicy.AlignmentType.idv.equals(promptDecision.getAlignment())) {
                    querySessionService.updateSessionPolicy(sessionID, "idv", details);
                    idvTriggered = true;
                    // feedback.getExecutionSteps().add(ActionPlan.builder().tool("idv").build());
                    
                    // // Complete the IDV process, works similar to HIL feedback request if we don't have a token
                    // try {
                    //     String verificationToken = completeIdvProcess(feedback);
                        
                    //     if (verificationToken != null) {
                    //         this.currentVerificationToken = verificationToken;
                    //         processedPrompt = "Identity verification completed successfully with token available. Now we can proceed with " + prompt;
                    //     } else {
                    //         feedback.setErrorMessage("Identity verification failed");
                    //         return feedback;
                    //     }
                    // } catch (Exception e) {
                    //     log.error("IDV process failed", e);
                    //     feedback.setErrorMessage("Identity verification process failed: " + e.getMessage());
                    //     return feedback;
                    // }
                }

                if (!idvTriggered) {
                    querySessionService.updateSessionPolicy(sessionID, "allow", "All policies allowed");
                }
            }

            // Julio - Changed just to log end of query session
            feedback.setExecutionSteps(new ArrayList<>(List.of(ActionPlan.builder().tool("create-plan").build())));
            log.info("Entering feedback loop with prompt: {}", processedPrompt);
            ActionFeedbackResponse result = executeFeedbackLoop(processedPrompt, feedback, sessionID);

            if (!querySessionService.getQuerySessionPausedBoolean(sessionID)) {
                querySessionService.endQuerySession(sessionID);
            }
            return result;
            
        } catch (Exception e) {
            log.error("Error in controller agent execution", e);
            feedback.setErrorMessage("Execution error: " + e.getMessage());
            //Julio
            querySessionService.endQuerySession(sessionID);
            return feedback;
        }
    }
    // Julio -Default no resume constructor
    public ActionFeedbackResponse testControllerAgent(String prompt, String sessionID){
        return testControllerAgent(prompt, sessionID, false);
    }
    
    // Julio - added sessionID parameter so we can log with logging service
    private ActionFeedbackResponse executeFeedbackLoop(String prompt, ActionFeedbackResponse feedback, String sessionID) {
        
        // Continue while there are execution steps remaining
        for (int iteration = 1; iteration <= MAX_FEEDBACK_LOOPS && !feedback.getExecutionSteps().isEmpty(); iteration++) {
            // Julio
            String actionID = actionPlanService.createActionPlan(sessionID, iteration);

            feedback.setLoopIteration(iteration);
            
            // Get next execution step
            ActionPlan currentStep = feedback.getExecutionSteps().removeFirst();
            log.info("Executing step: {}", currentStep);

            
            // For other steps, prepare action plan
            CheckOutputIn actionPlan = feedback.getActionPlan() == null ? prepareActionPlan(prompt) : feedback.getActionPlan();
            feedback.setActionPlan(actionPlan);

            if (actionPlan == null || actionPlan.getLlmOutput() == null) {
                feedback.setErrorMessage("Failed to prepare action plan");
                // Julio
                actionPlanService.updateActionPlan(actionID, "Failed to prepare action plan");
                return feedback;
            }
            // Julio
            actionPlanService.updateActionPlan(actionID, actionPlan.getLlmOutput());

            feedback.setExecutionSteps(new ArrayList<>(actionPlan.getActions()));

            //Julio
            for (ActionPlan action : actionPlan.getActions()) {
                // log all tools with pending tool status. no id yet
                String toolRequestId = toolRequestService.createToolRequest(actionID, action.getTool(), action.getParameters().toString());
                action.setToolRequestId(toolRequestId);
            }
            
            // Julio
            String policyCheckId = policyCheckService.createPolicyCheck(actionID);

            String policyPromptWithContext = contextService.buildPolicyPromptWithContext(
                sessionID, 
                actionPlan.getLlmOutput(), 
                actionPlan.getActions(),
                actionID
            );
            
            CheckOutputRequest policyRequest = CheckOutputRequest.builder()
                .llmOutput(policyPromptWithContext)  // Julio - Using context + prompt instead
                .actions(actionPlan.getActions() != null ? actionPlan.getActions() : Collections.emptyList())
                .build();
            
            feedback.setCheckOutputRequest(policyRequest);
            
            Optional<DecisionOut> policyDecision = policyApi.checkOutput(policyRequest);
            
            if (policyDecision.isEmpty()) {
                feedback.setErrorMessage("Policy validation failed");
                // Julio
                policyCheckService.completePolicyCheck(policyCheckId, "error: null policy decision", "N/A: policy validation error", "N/A: policy validation error");
                return feedback;
            }

            DecisionOut outputDecision = policyDecision.get();
            String details = "Triggered policy: " + outputDecision.getPolicyId() + "\n Reason: " + outputDecision.getReason();
            // DecisionOut outputDecision = DecisionOut.builder().alignment(PerPolicy.AlignmentType.hil).build();
            feedback.setOutputFeedback(outputDecision);
            
            if (PerPolicy.AlignmentType.deny.equals(outputDecision.getAlignment())) {
                feedback.setCompleted(true);
                // Julio
                policyCheckService.completePolicyCheck(policyCheckId, "completed", "deny", details);
                return feedback;
            }


            if (PerPolicy.AlignmentType.idv.equals(outputDecision.getAlignment())) {
                // Julio
                policyCheckService.completePolicyCheck(policyCheckId, "completed", "idv", details);
                String toolRequestId = toolRequestService.createToolRequest(actionID, "idv", null);
                toolRequestService.initiateToolExecution(toolRequestId);

                // Get user email (in real implementation, get from user context)
                String userEmail = getCurrentUserEmail();
                
                currentVerificationToken = verificationService.getToken(userEmail).getToken();

                // Check if we already have a token
                if (currentVerificationToken != null && !currentVerificationToken.isEmpty()) {
                    // We have token, validate and if not then clear and request new verification
                    log.info("Validating existing verification token");
                    TokenValidationResponse tokenValidation = new TokenValidationResponse();
                    tokenValidation = verificationService.validateToken(currentVerificationToken);
                    
                    if (tokenValidation.isValid()) {
                        log.info("Existing token is valid, proceeding with execution");
                        toolRequestService.completeToolExecution(toolRequestId, "success", "Valid token found");
                        outputDecision.setAlignment(PerPolicy.AlignmentType.allow);
                    } else {
                        log.info("Existing token is invalid, clearing and requesting new verification");
                        currentVerificationToken = null;
                    }
                }
                // // Get token from IDV-MCP, if exists

                // currentVerificationToken = verificationService.getToken(userEmail).getToken();

                // If the following statement is true, we need to request verification
                if (currentVerificationToken == null || currentVerificationToken.isEmpty()) {
                    log.info("No valid token found, starting verification process");

                    // Start verification process
                    StartVerificationResponse startResp = verificationService.startVerification(userEmail);
                    log.info("Verification start response: {}", startResp);
                    if (startResp.isSuccess()) {
                        log.info("Verification started successfully");
                        String idvMessage = createIdvPremadeMessage(startResp.getVerificationLink());
                        
                        // Set status of all current tools in feedback to not completed
                        for (ActionPlan action : actionPlan.getActions()) {
                            toolRequestService.completeToolExecution(toolRequestId, "not_completed", "Not completed because IDV was required");
                        }
                        
                        // Must do so that get-results tool can return message
                        feedback.setFinalResult(idvMessage);
                        feedback.setCompleted(true);
                        querySessionService.pauseQuerySession(sessionID);
                        log.info("Verification message for user: {}", feedback);

                        return feedback;
                    } else {
                        log.error("Failed to start verification process");
                        toolRequestService.completeToolExecution(toolRequestId, "failed", "Failed to start verification");
                        feedback.setErrorMessage("Failed to start identity verification process");
                        return feedback;
                    }
                }
                
            }

            if (PerPolicy.AlignmentType.hil.equals(outputDecision.getAlignment())) {
                // Generate HIL feedback response
                policyCheckService.completePolicyCheck(policyCheckId, "completed", "hil", details);
                
                // Create HIL feedback response using the previous action plan
                CheckOutputIn hilRequestText = generateHilFeedbackResponse(prompt, actionPlan, feedback);

                // Set status of all current tools in feedback to not completed because they were planned but wont be executed
                for (ActionPlan action : hilRequestText.getActions()) {
                    toolRequestService.completeToolExecution(action.getToolRequestId(), "not_completed", "Not completed because HIL feedback was requested instead");
                }
                
                // Set the HIL response in feedback and mark as completed
                feedback.setActionPlan(hilRequestText);
                feedback.setCompleted(true);

                // Create and initiate hil tool request
                String toolRequestId = toolRequestService.createToolRequest(actionID, "hil_feedback", hilRequestText.getLlmOutput());
                hilRequestText.getActions().get(0).setToolRequestId(toolRequestId);
                toolRequestService.initiateToolExecution(toolRequestId);
                
                querySessionService.pauseQuerySession(sessionID);
                 
                return feedback;
            }

            if (PerPolicy.AlignmentType.allow.equals(outputDecision.getAlignment())) {
                // Execute tools if available
                policyCheckService.completePolicyCheck(policyCheckId, "completed", "allow", "All policies allowed");
                if (!CollectionUtils.isEmpty(actionPlan.getActions())) {
                    for (ActionPlan action : actionPlan.getActions()) {
                        //Julio
                        // start tool exec should find correct tool request and assign id to it
                        String toolRequestId = toolRequestService.initiateToolExecution(action.getToolRequestId());

                        String toolResult = executeToolAction(action, feedback);
                        //Julio
                        toolRequestService.completeToolExecution(toolRequestId, "success", toolResult);
                        
                        // Julio - added feedback lifecycle logging
                        feedbackService.createFeedbackRequest(actionID);

                        feedback.getToolExecutionResults().put(action.getTool(), toolResult);
                        feedback.setExecutionSteps(actionPlan.getActions().stream().filter(a -> !a.getTool().equals(action.getTool())).collect(Collectors.toList()));
                        // Add any new execution steps based on tool results if needed
                    }
                    addNewExecutionStepsIfNeeded(feedback, sessionID);
                    //Julio
                } else {
                    // No actions to execute for this step
                    log.info("No actions to execute for step: {}", currentStep);
                }
            }
            if (feedback.getExecutionSteps().isEmpty()) {
                // No more execution steps - end feedback and stop looping, add actual feedback as param
                feedbackService.completeFeedbackRequest(actionID, feedback.getFinalResult(), "end_loop");
            } else {
                // More execution steps available - continue looping, add actual feedback as param
                feedbackService.completeFeedbackRequest(actionID, feedback.getFinalResult(), "continue_loop");
            }
            // Julio
        }
        
        // Mark as completed when all execution steps are done
        feedback.setCompleted(true);
        return feedback;
    }
    
    private String executeToolAction(ActionPlan action, ActionFeedbackResponse feedback) {
        String toolInstruction = formatToolInstruction(action);
        feedback.getToolExecutions().add("Executing: " + toolInstruction);
        
        // Julio - We now execute tools with the openai chat client, which holds only external tools
        String result = openAiChatClient.prompt()
            .system("Execute ONLY the requested tool with the provided parameters. Return only the direct tool execution result, do not include any other text, this task is case sensitive.")
            .user(toolInstruction)
            .call()
            .content();
        feedback.getToolExecutions().add("Result: " + result);
        
        if (feedback.getFinalResult() == null) {
            feedback.setFinalResult(result);
        } else {
            feedback.setFinalResult(feedback.getFinalResult() + "; " + result);
        }
        
        return result;
    }
    
    private String formatToolInstruction(ActionPlan action) {
        StringBuilder instruction = new StringBuilder();
        instruction.append("Execute tool: ").append(action.getTool());
        
        // Combine original parameters with verification token if available
        Map<String, Object> allParameters = new HashMap<>();
        if (action.getParameters() != null) {
            allParameters.putAll(action.getParameters());
        }
        log.info("Parameters: {}", allParameters);
        
        // Julio - Only add verification token if available and required by the tool
        if (allParameters.containsKey("token")) {
            if (currentVerificationToken != null) {
                allParameters.put("token", currentVerificationToken);
            }
            else {
                allParameters.put("token", "temp-token");
            }
        }
        if (allParameters.containsKey("auth_token")) {
            if (currentVerificationToken != null) {
                allParameters.put("auth_token", currentVerificationToken);
            }
            else {
                allParameters.put("auth_token", "temp-token");
            }
        }
        
        if (!allParameters.isEmpty()) {
            instruction.append(" with parameters: ");
            allParameters.forEach((key, value) -> 
                instruction.append(key).append("=").append(value).append(" "));
        }
        
        return instruction.toString().trim();
    }
    
    private void addNewExecutionStepsIfNeeded(ActionFeedbackResponse feedback, String sessionID) {
        String result = feedback.getToolExecutionResults().entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        String previousPlannedActions = feedback.getActionPlan().getActions().stream().map(ActionPlan::getTool).collect(Collectors.joining(", "));
        String previousActionPlan = feedback.getActionPlan().getLlmOutput();
        String userPrompt = "Initial user request, must be completed in full before processing ends: {" + feedback.getPrompt() + "}. Previous plan: {" + previousActionPlan + "}. Planned actions: {" + previousPlannedActions + "}. Result of executed actions: {" + result + "}. Should there be any new actions planned based on this result to complete the user request? If yes, please provide a new plan for the next steps.";
        log.info("Feedback prompt: {}", userPrompt);
        String systemPrompt = "Context summary: " + contextService.buildContextSummary(sessionID) + "\n\n" + promptsUtil.newPlanPrompt() + "\n\n Current token for tool calls that require it: " + currentVerificationToken;
        CheckOutputIn newPlan = openAiChatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(CheckOutputIn.class);
        if (newPlan != null && newPlan.getActions() != null && !newPlan.getActions().isEmpty()) {
            log.info("Adding new execution steps based on tool results: {}", newPlan.getActions());
            feedback.getExecutionSteps().addAll(newPlan.getActions());
            feedback.setActionPlan(newPlan);
        } else {
            log.info("No new actions planned based on tool results.");
            feedback.setExecutionSteps(new ArrayList<>());
        }
    }

    private CheckOutputIn prepareActionPlan(String prompt) {
        String systemPrompt = promptsUtil.actionPlanSystemPrompt() + "\n\n Current token for tool calls that require it: " + currentVerificationToken;
        return openAiChatClient.prompt()
                .system(systemPrompt)
                .user(prompt)
                .call()
                .entity(CheckOutputIn.class);
    }

    // Julio - Gets breakdown and follow up questions for user to be displayed during HIL feedback request
    private CheckOutputIn generateHilFeedbackResponse(String prompt, CheckOutputIn actionPlan, ActionFeedbackResponse previousFeedback) {
        // Build context for HIL prompt
        String context = ("Initial User Request: ") + (prompt) + ("\n\n");
        context += ("Proposed Action Plan: ") + (actionPlan.getLlmOutput()) + ("\n\n");
        
        if (actionPlan.getActions() != null) {
            context += ("Proposed tool executions: ");
            for (ActionPlan action : actionPlan.getActions()) {
                context += (action.getTool()) + (" ");
            }
        }
        
        return openAiChatClient.prompt()
                .system(promptsUtil.hilFeedbackPrompt())
                .user(context)
                .call()
                .entity(CheckOutputIn.class);
    }

    private Optional<DecisionOut> validateClientPrompt(String prompt) {
        return policyApi.checkPrompt(CheckPromptIn.builder().prompt(prompt).build());
    }
    
    /**
     * Complete the 3-step IDV process:
     * 1. Start verification process
     * 2. Poll by trace ID until status is SUCCESS or FAILED
     * 3. Get token for the email that started verification
     */
    private String completeIdvProcess(ActionFeedbackResponse feedback) {
        // Use a default email for IDV process - in real implementation this would come from the user context
        String userEmail = "ognjen.samardzic@incode.com";
        
        try {
            // Step 1: Start verification process
            log.info("Starting verification for user: {}", userEmail);
            var startResp = verificationService.startVerification(userEmail);
            var traceId = startResp.getVerificationTraceId();
            
            if (traceId == null) {
                log.error("Failed to start verification process for user: {}", userEmail);
                return null;
            }
            log.info("STARTED verification trace ID: {}", traceId);
            
            // Send verification link email using the link from startResp
            try {
                EmailRequest emailRequest = EmailRequest.builder()
                    .toEmail("osamardzic@gmail.com")
                    .fromEmail("jocca1985@gmail.com")
                    .subject("Verification link")
                    .content("Here is the link to verification: " + startResp.getVerificationLink())
                    .build();
                
                var emailResponse = emailClientApi.sendEmail(emailRequest);
                if (emailResponse.isSuccess()) {
                    log.info("Verification email sent successfully to: {}", userEmail);
                } else {
                    log.warn("Failed to send verification email: {}", emailResponse.getMessage());
                }
            } catch (Exception e) {
                log.error("Error sending verification email to: {}", userEmail, e);
            }

            
            log.info("Verification started with trace ID: {}", traceId);
            
            // Step 2: Poll verification status until completion
            log.info("Polling verification status for trace ID: {}", traceId);
            VerificationStatusResponse statusResponse = verificationService.pollVerificationStatus(traceId);
            
            if (statusResponse == null) {
                log.error("Failed to get verification status for trace ID: {}", traceId);
                return null;
            }
            
            String status = statusResponse.getStatus();
            log.info("Final verification status: {}", status);
            
            if (!"SUCCESS".equalsIgnoreCase(status)) {
                log.warn("Verification failed or timed out with status: {}", status);
                return null;
            }
            
            // Step 3: Get token for the verified user
            log.info("Getting authentication token for user: {}", userEmail);
            TokenResponse tokenResponse = verificationService.getToken(userEmail);
            
            if (tokenResponse == null || !tokenResponse.isSuccess() || tokenResponse.getToken() == null) {
                log.error("Failed to get token for user: {}", userEmail);
                return null;
            }
            
            log.info("IDV process completed successfully for user: {}", userEmail);
            return tokenResponse.getToken();
            
        } catch (Exception e) {
            log.error("Error during IDV process for user: {}", userEmail, e);
            throw e;
        }
    }

    private String createIdvPremadeMessage(String verificationLink) {
        return String.format("""
            🔐 **Identity Verification Required**
            
            Your request requires identity verification before we can proceed:
            
            **Next Steps:**
            1. Click the verification link below to complete your identity verification
            2. Follow the verification process (ID scan, face verification, etc.)
            3. Once completed, return here and we'll continue with your request
            
            **Verification Link:** %s
            
            This verification helps us ensure the security of your account and comply with our policies. The verification process typically takes 2-3 minutes to complete.
            
            Once you've completed the verification, please let us know and we'll continue processing your request.

            Note: If you are an MCP client, call resume-processing tool with the session ID and feedback about how idv went to resume processing.
            """,
            verificationLink
        );
    }

    private String getCurrentUserEmail() {
        // In real implementation, get from user context/session
        return "ognjen.samardzic@incode.com";
    }
}
