package com.incodelabs.alignedexecutionengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.incodelabs.alignedexecutionengine.repository.ToolRequestRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolRequestService {

    private final ToolRequestRepository toolRequestRepository;

    // Create a new tool request and return the toolRequestId
    public String createToolRequest(String actionId, String toolName, String requestParameters) {
        String toolRequestId = UUID.randomUUID().toString();
        toolRequestRepository.createToolRequest(toolRequestId, actionId, toolName, requestParameters);
        return toolRequestId;
    }

    // Retrieve a tool request by toolRequestId
    public Map<String, Object> getToolRequest(String toolRequestId) {
        return toolRequestRepository.getToolRequest(toolRequestId);
    }

    // Retrieve all tool requests for an action
    public List<Map<String, Object>> getToolRequestsByActionPlan(String actionId) {
        return toolRequestRepository.getToolRequestsByActionPlan(actionId);
    }

    // Initiate tool execution
    public String initiateToolExecution(String toolRequestId) {
        return toolRequestRepository.initiateToolExecution(toolRequestId);
    }

    // Complete tool execution
    public void completeToolExecution(String toolRequestId, String finalStatus, String responseData) {
        toolRequestRepository.completeToolExecution(toolRequestId, finalStatus, responseData);
    }

    // Update tool status
    public void updateToolStatus(String toolRequestId, String status) {
        toolRequestRepository.updateToolStatus(toolRequestId, status);
    }

    // Get tool status
    public String getToolStatus(String toolRequestId) {
        return toolRequestRepository.getToolStatus(toolRequestId);
    }

    // Delete a tool request by toolRequestId
    public void deleteToolRequest(String toolRequestId) {
        toolRequestRepository.deleteToolRequest(toolRequestId);
    }
}
