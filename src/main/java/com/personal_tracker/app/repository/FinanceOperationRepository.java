package com.personal_tracker.app.repository;

import com.personal_tracker.app.entity.FinanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FinanceOperationRepository extends JpaRepository<FinanceOperation, Long> {
    @Query("""
            SELECT operation
            FROM FinanceOperation operation
            WHERE operation.user.id = :userId
              AND (operation.status IS NULL OR UPPER(operation.status) = 'OK')
            ORDER BY operation.operationDate DESC
            """)
    List<FinanceOperation> findVisibleByUserIdOrderByOperationDateDesc(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT COALESCE(NULLIF(TRIM(operation.category), ''), 'Без категории')
            FROM FinanceOperation operation
            WHERE operation.user.id = :userId
              AND (operation.status IS NULL OR UPPER(operation.status) = 'OK')
            ORDER BY COALESCE(NULLIF(TRIM(operation.category), ''), 'Без категории')
            """)
    List<String> findVisibleCategoriesByUserId(@Param("userId") Long userId);

    List<FinanceOperation> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    List<FinanceOperation> findByUserIdAndOperationDateBetween(Long userId, LocalDateTime from, LocalDateTime to);

    boolean existsByUserIdAndOperationKey(Long userId, String operationKey);

    long deleteByUserIdAndIdIn(Long userId, Collection<Long> ids);

    long deleteByUserIdAndOperationDateBetween(Long userId, LocalDateTime from, LocalDateTime to);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM finance_operations
                WHERE user_id = :userId
                  AND operation_date IS NOT DISTINCT FROM :operationDate
                  AND operation_amount = :operationAmount
                  AND operation_currency IS NOT DISTINCT FROM :operationCurrency
                  AND category IS NOT DISTINCT FROM :category
                  AND description IS NOT DISTINCT FROM :description
            )
            """, nativeQuery = true)
    boolean existsSimilarOperation(
            @Param("userId") Long userId,
            @Param("operationDate") LocalDateTime operationDate,
            @Param("operationAmount") BigDecimal operationAmount,
            @Param("operationCurrency") String operationCurrency,
            @Param("category") String category,
            @Param("description") String description
    );
}
