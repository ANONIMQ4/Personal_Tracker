package com.personal_tracker.app.controller;

import com.personal_tracker.app.entity.FinanceOperation;
import com.personal_tracker.app.service.FinanceAnalyticsService;
import com.personal_tracker.app.service.FinanceAnalyticsService.AnalyticsResponse;
import com.personal_tracker.app.service.FinanceCategoryService;
import com.personal_tracker.app.service.FinanceCategoryService.CategoryDto;
import com.personal_tracker.app.service.FinanceOperationService;
import com.personal_tracker.app.service.FinanceOperationService.ImportResult;
import com.personal_tracker.app.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
public class FinanceOperationController {

    private static final int MAX_UPLOAD_FILES = 20;

    private final CurrentUserService currentUserService;
    private final FinanceAnalyticsService financeAnalyticsService;
    private final FinanceCategoryService financeCategoryService;
    private final FinanceOperationService financeOperationService;

    public FinanceOperationController(
            CurrentUserService currentUserService,
            FinanceAnalyticsService financeAnalyticsService,
            FinanceCategoryService financeCategoryService,
            FinanceOperationService financeOperationService
    ) {
        this.currentUserService = currentUserService;
        this.financeAnalyticsService = financeAnalyticsService;
        this.financeCategoryService = financeCategoryService;
        this.financeOperationService = financeOperationService;
    }

    @GetMapping("/finance/operations")
    public ResponseEntity<List<FinanceOperation>> getOperations(HttpSession session) {
        return currentUserService.get(session)
                .map(user -> ResponseEntity.ok(financeOperationService.getOperations(user.getId())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @GetMapping("/finance/categories")
    public ResponseEntity<List<CategoryDto>> getCategories(HttpSession session) {
        return currentUserService.get(session)
                .map(user -> ResponseEntity.ok(financeCategoryService.getUserCategories(user.getId())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @GetMapping("/finance/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "metric", required = false, defaultValue = "amount") String metric,
            @RequestParam(value = "donutMode", required = false, defaultValue = "expense") String donutMode,
            @RequestParam(value = "categoryKeys", required = false) List<String> categoryKeys,
            HttpSession session
    ) {
        return currentUserService.get(session)
                .map(user -> ResponseEntity.ok(financeAnalyticsService.buildAnalytics(
                        user.getId(),
                        from,
                        to,
                        metric,
                        donutMode,
                        categoryKeys
                )))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/finance/upload")
    public ResponseEntity<UploadResult> uploadOperations(
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpSession session
    ) {
        return currentUserService.get(session)
                .map(user -> {
                    try {
                        List<MultipartFile> uploadFiles = collectUploadFiles(files, file);
                        if (uploadFiles.isEmpty()) {
                            return ResponseEntity.badRequest().body(new UploadResult(0, 0));
                        }

                        int importedCount = 0;
                        int skippedCount = 0;
                        for (MultipartFile uploadFile : uploadFiles) {
                            ImportResult result = financeOperationService.importOperations(user, uploadFile);
                            importedCount += result.importedCount();
                            skippedCount += result.skippedCount();
                        }
                        return ResponseEntity.ok(new UploadResult(importedCount, skippedCount));
                    } catch (IOException | IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().body(new UploadResult(0, 0));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    private List<MultipartFile> collectUploadFiles(MultipartFile[] files, MultipartFile file) {
        List<MultipartFile> uploadFiles = new ArrayList<>();
        if (files != null) {
            for (MultipartFile uploadFile : files) {
                if (uploadFile != null && !uploadFile.isEmpty()) {
                    uploadFiles.add(uploadFile);
                }
            }
        }
        if (file != null && !file.isEmpty()) {
            uploadFiles.add(file);
        }
        if (uploadFiles.size() > MAX_UPLOAD_FILES) {
            throw new IllegalArgumentException("Слишком много файлов");
        }
        uploadFiles.forEach(this::validateUploadFile);
        return uploadFiles;
    }

    private void validateUploadFile(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new IllegalArgumentException("Поддерживаются только XLS/XLSX файлы");
        }
    }

    @PostMapping("/finance/operations")
    public ResponseEntity<?> createOperation(
            @RequestBody ManualOperationRequest request,
            HttpSession session
    ) {
        return currentUserService.get(session)
                .map(user -> {
                    try {
                        FinanceOperation operation = financeOperationService.createManualOperation(
                                user,
                                request.type(),
                                request.amount(),
                                request.currency(),
                                request.category(),
                                request.description(),
                                request.operationDate()
                        );
                        return ResponseEntity.ok(operation);
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.status(409).body(new ErrorResponse("Такая операция уже добавлена"));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @DeleteMapping("/finance/operations")
    public ResponseEntity<DeleteResult> deleteOperations(
            @RequestBody DeleteOperationsRequest request,
            HttpSession session
    ) {
        return currentUserService.get(session)
                .map(user -> {
                    long deletedCount = financeOperationService.deleteOperations(user.getId(), request.ids());
                    return ResponseEntity.ok(new DeleteResult(deletedCount));
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PatchMapping("/finance/operations/{id}")
    public ResponseEntity<FinanceOperation> updateOperation(
            @PathVariable Long id,
            @RequestBody UpdateOperationRequest request,
            HttpSession session
    ) {
        return currentUserService.get(session)
                .map(user -> {
                    try {
                        FinanceOperation operation = financeOperationService.updateOperation(
                                user,
                                id,
                                request.operationAmount(),
                                request.category(),
                                request.description()
                        );
                        return ResponseEntity.ok(operation);
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().<FinanceOperation>build();
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @DeleteMapping("/finance/operations/period")
    public ResponseEntity<DeleteResult> deleteOperationsByPeriod(
            @RequestBody DeletePeriodRequest request,
            HttpSession session
    ) {
        return currentUserService.get(session)
                .map(user -> {
                    try {
                        long deletedCount = financeOperationService.deleteOperationsByPeriod(
                                user.getId(),
                                request.from(),
                                request.to()
                        );
                        return ResponseEntity.ok(new DeleteResult(deletedCount));
                    } catch (IllegalArgumentException exception) {
                        return ResponseEntity.badRequest().body(new DeleteResult(0));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    public record UploadResult(int importedCount, int skippedCount) {
    }

    public record ManualOperationRequest(
            String type,
            BigDecimal amount,
            String currency,
            String category,
            String description,
            LocalDateTime operationDate
    ) {
    }

    public record DeleteOperationsRequest(List<Long> ids) {
    }

    public record DeletePeriodRequest(LocalDate from, LocalDate to) {
    }

    public record UpdateOperationRequest(
            BigDecimal operationAmount,
            String category,
            String description
    ) {
    }

    public record DeleteResult(long deletedCount) {
    }

    public record ErrorResponse(String message) {
    }
}
