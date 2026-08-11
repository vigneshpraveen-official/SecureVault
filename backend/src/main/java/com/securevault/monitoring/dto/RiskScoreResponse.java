package com.securevault.monitoring.dto;

import java.util.List;

/** See MonitoringController for the exact, documented formula — no ML, no magic (P5.5 step 5). */
public record RiskScoreResponse(int score, List<String> contributingFactors) {}
