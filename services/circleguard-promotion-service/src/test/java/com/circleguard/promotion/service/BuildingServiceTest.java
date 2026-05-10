package com.circleguard.promotion.service;

import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BuildingServiceTest {

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorRepository floorRepository;

    private BuildingService buildingService;

    @BeforeEach
    void setUp() {
        buildingService = new BuildingService(buildingRepository, floorRepository);
    }

    @Test
    void createBuilding_withValidData_persistsBuildingWithCorrectFields() {
        Building saved = Building.builder()
                .id(UUID.randomUUID()).name("Main Hall").code("MH-01")
                .description("Main campus hall").latitude(4.6097).longitude(-74.0817)
                .address("Carrera 7 #40-62").build();
        when(buildingRepository.save(any(Building.class))).thenReturn(saved);

        Building result = buildingService.createBuilding(
                "Main Hall", "MH-01", "Main campus hall", 4.6097, -74.0817, "Carrera 7 #40-62");

        assertThat(result.getName()).isEqualTo("Main Hall");
        assertThat(result.getCode()).isEqualTo("MH-01");
        verify(buildingRepository).save(any(Building.class));
    }

    @Test
    void deleteBuilding_withNoFloors_deletesSuccessfully() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findByBuildingId(id)).thenReturn(List.of());

        buildingService.deleteBuilding(id);

        verify(buildingRepository).deleteById(id);
    }

    @Test
    void deleteBuilding_withExistingFloors_throwsRuntimeException() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findByBuildingId(id)).thenReturn(List.of(new Floor()));

        assertThatThrownBy(() -> buildingService.deleteBuilding(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("floors");
    }

    @Test
    void updateBuilding_withUnknownId_throwsRuntimeException() {
        UUID id = UUID.randomUUID();
        when(buildingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> buildingService.updateBuilding(
                id, "New Name", "NN-01", "Desc", 0.0, 0.0, "Addr"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateBuilding_withExistingId_updatesAllFields() {
        UUID id = UUID.randomUUID();
        Building existing = Building.builder()
                .id(id).name("Old Name").code("OLD-01").build();
        when(buildingRepository.findById(id)).thenReturn(Optional.of(existing));
        when(buildingRepository.save(any(Building.class))).thenAnswer(inv -> inv.getArgument(0));

        Building result = buildingService.updateBuilding(
                id, "New Name", "NEW-01", "New Desc", 5.0, -75.0, "New Addr");

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getCode()).isEqualTo("NEW-01");
    }
}
