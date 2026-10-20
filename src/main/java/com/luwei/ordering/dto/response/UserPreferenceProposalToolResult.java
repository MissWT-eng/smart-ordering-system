package com.luwei.ordering.dto.response;

public record UserPreferenceProposalToolResult(
        Status status,
        UserPreferenceProposalDTO proposal,
        String message,
        String nextAction
        )
{
    public enum Status{
        SUCCESS,
        INVALID_ARGUMENTS,
        LOGIN_REQUIRED
    }
}
