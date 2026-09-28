package com.paulcartagena.icarapi.income.entity;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.donation.entity.Donation;
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
@Table(name = "income")
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToOne
    @JoinColumn(name = "source_donation_id", unique = true)
    private Donation sourceDonation;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 250)
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "registered_by")
    private AppUser registeredBy;
}
