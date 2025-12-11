package gc.demo.dto.response;

public record SyncResult(int total, int inserted, int updated, int deleted) {}
