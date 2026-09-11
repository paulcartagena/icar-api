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
@Table(name = "about_us")
public class AboutUs {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 250, nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;
}
