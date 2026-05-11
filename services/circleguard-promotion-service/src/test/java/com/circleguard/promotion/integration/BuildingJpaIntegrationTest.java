package com.circleguard.promotion.integration;

import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Tag("testcontainers")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BuildingJpaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Test
    void save_building_persistsToRealPostgres() {
        Building building = Building.builder()
                .name("Engineering Block").code("EB-01")
                .description("Main engineering building")
                .latitude(4.6097).longitude(-74.0817)
                .address("Carrera 7 #40-62")
                .build();

        Building saved = buildingRepository.save(building);

        assertThat(saved.getId()).isNotNull();
        assertThat(buildingRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void findByCode_returnsCorrectBuilding() {
        Building building = Building.builder()
                .name("Library").code("LIB-01").description("Main library")
                .latitude(4.61).longitude(-74.08).address("Campus Ave 100")
                .build();
        buildingRepository.save(building);

        assertThat(buildingRepository.findByCode("LIB-01"))
                .isPresent()
                .get()
                .extracting(Building::getName)
                .isEqualTo("Library");
    }

    @Test
    void findAll_returnsAllPersisted() {
        buildingRepository.save(Building.builder().name("A").code("A-01")
                .latitude(0.0).longitude(0.0).address("A St").build());
        buildingRepository.save(Building.builder().name("B").code("B-01")
                .latitude(0.0).longitude(0.0).address("B St").build());

        List<Building> all = buildingRepository.findAll();

        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void findFloorsByBuilding_withNoFloors_returnsEmptyList() {
        Building building = buildingRepository.save(
                Building.builder().name("Empty Block").code("EMP-01")
                        .latitude(0.0).longitude(0.0).address("Empty St").build()
        );

        assertThat(floorRepository.findByBuildingId(building.getId())).isEmpty();
    }
}
