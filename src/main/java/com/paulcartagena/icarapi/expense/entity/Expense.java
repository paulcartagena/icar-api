package com.paulcartagena.icarapi.expense.entity;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 250)
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @ManyToOne
    @JoinColumn(name = "registered_by")
    private AppUser registeredBy;
}
