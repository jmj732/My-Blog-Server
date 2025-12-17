package gc.demo.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record SearchRequest(@NotBlank String q, Integer limit, List<Double> embedding) {}
