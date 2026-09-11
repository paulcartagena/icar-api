package com.paulcartagena.icarapi.sitecontent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "contact_info")
public class ContactInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 250, nullable = false)
    private String address;

    @Column(length = 25, nullable = false)
    private String phone;

    @Column(length = 150, nullable = false)
    private String email;

    @Column(name = "map_url", length = 500, nullable = false)
    private String mapUrl;
}
