package com.example.k8soperator.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class MaintenanceWindowLogicTest {

  @Test
  void withinSameDayWindow() {
    LocalTime start = LocalTime.of(9, 0);
    LocalTime end = LocalTime.of(18, 0);
    assertThat(MaintenanceWindowReconciler.withinWindow(LocalTime.of(12, 0), start, end)).isTrue();
    assertThat(MaintenanceWindowReconciler.withinWindow(LocalTime.of(8, 59), start, end)).isFalse();
    assertThat(MaintenanceWindowReconciler.withinWindow(LocalTime.of(18, 0), start, end)).isFalse();
  }

  @Test
  void withinOvernightWindow() {
    LocalTime start = LocalTime.of(22, 0);
    LocalTime end = LocalTime.of(6, 0);
    assertThat(MaintenanceWindowReconciler.withinWindow(LocalTime.of(23, 0), start, end)).isTrue();
    assertThat(MaintenanceWindowReconciler.withinWindow(LocalTime.of(3, 0), start, end)).isTrue();
    assertThat(MaintenanceWindowReconciler.withinWindow(LocalTime.of(12, 0), start, end)).isFalse();
  }
}
