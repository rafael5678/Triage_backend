package com.hospital.triage.auth.application.service;

import com.hospital.triage.common.ColombianMobile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColombianMobileTest {

    @Test
    @DisplayName("WA-01: Un celular colombiano real queda con indicativo 57")
    void wa01_normaliza() {
        assertThat(ColombianMobile.normalize("300 123 4567")).isEqualTo("573001234567");
        assertThat(ColombianMobile.normalize("3186680042")).isEqualTo("573186680042");
        assertThat(ColombianMobile.normalize("573121112233")).isEqualTo("573121112233");
        assertThat(ColombianMobile.normalize("")).isNull();
        assertThat(ColombianMobile.normalize(null)).isNull();
    }

    @Test
    @DisplayName("WA-02: Números inventados no pasan")
    void wa02_rechazaInventados() {
        assertThatThrownBy(() -> ColombianMobile.normalize("1111111111"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ColombianMobile.normalize("123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ColombianMobile.normalize("6012345678"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
