package com.incodelabs.alignedexecutionengine.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class ToolRequestRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Create a new tool request
    public void createToolRequest(String toolRequestId, String actionId, String toolName, String requestParameters) {
        String sql = "INSERT INTO tool_requests (tool_request_id, action_id, policy_id, tool_status, tool_name, request_parameters) VALUES (?, ?, ?, 'PENDING', ?, ?)";
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, toolRequestId);
            ps.setString(2, actionId);
            ps.setString(3, null); // policy_id placeholder
            ps.setString(4, toolName);
            ps.setString(5, requestParameters);
            return ps;
        });
    }

    // Retrieve a tool request by toolRequestId
    public Map<String, Object> getToolRequest(String toolRequestId) {
        String sql = "SELECT * FROM tool_requests WHERE tool_request_id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, toolRequestId);
        return results.isEmpty() ? null : results.get(0);
    }

    // Retrieve all tool requests for an action
    public List<Map<String, Object>> getToolRequestsByActionPlan(String actionPlanId) {
        String sql = "SELECT * FROM tool_requests WHERE action_id = ? ORDER BY exec_start_time DESC";
        return jdbcTemplate.queryForList(sql, actionPlanId);
    }

    // Initiate tool execution
    public String initiateToolExecution(String actionId, String toolName) {
        String toolRequestId = jdbcTemplate.queryForObject(
            // select the first pending tool request for the action and tool
            "SELECT tool_request_id FROM tool_requests WHERE action_id = ? AND tool_name = ? AND tool_status = 'PENDING' LIMIT 1",
            String.class,
            actionId, toolName
        );
    
        String sql = "UPDATE tool_requests SET tool_status = 'INITIATED', exec_start_time = ? WHERE action_id = ? AND tool_name = ?";
        jdbcTemplate.update(sql, Timestamp.valueOf(LocalDateTime.now()), actionId, toolName);
    
        return toolRequestId;
    }

    // Complete tool execution
    public void completeToolExecution(String toolRequestId, String finalStatus, String responseData) {
        String sql = "UPDATE tool_requests SET tool_status = ?, exec_end_time = ?, response_data = ? WHERE tool_request_id = ?";
        jdbcTemplate.update(sql, finalStatus, Timestamp.valueOf(LocalDateTime.now()), responseData, toolRequestId);
    }

    // Update tool status
    public void updateToolStatus(String toolRequestId, String status) {
        String sql = "UPDATE tool_requests SET tool_status = ? WHERE tool_request_id = ?";
        jdbcTemplate.update(sql, status, toolRequestId);
    }

    // Get tool status
    public String getToolStatus(String toolRequestId) {
        String sql = "SELECT tool_status FROM tool_requests WHERE tool_request_id = ?";
        return jdbcTemplate.queryForObject(sql, String.class, toolRequestId);
    }

    // Delete a tool request by toolRequestId
    public void deleteToolRequest(String toolRequestId) {
        String sql = "DELETE FROM tool_requests WHERE tool_request_id = ?";
        jdbcTemplate.update(sql, toolRequestId);
    }
}
