package com.raditomo.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthHlsControllerTest {

    private final AuthHlsController controller = new AuthHlsController();

    @Test
    void returns_200_when_userId_in_path_matches_principal() {
        ResponseEntity<Void> r = controller.authHls(42L, "/hls/42/morning/20260424-0500/playlist.m3u8");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void returns_200_when_uri_has_query_string() {
        ResponseEntity<Void> r = controller.authHls(42L, "/hls/42/morning/20260424-0500/segment-001.ts?cache=1");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void returns_401_when_principal_is_null() {
        ResponseEntity<Void> r = controller.authHls(null, "/hls/42/x.m3u8");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returns_403_when_path_userId_differs_from_principal() {
        ResponseEntity<Void> r = controller.authHls(42L, "/hls/99/morning/20260424-0500/playlist.m3u8");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void returns_403_for_non_hls_path() {
        ResponseEntity<Void> r = controller.authHls(42L, "/api/auth/me");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void returns_403_for_missing_uri_header() {
        ResponseEntity<Void> r = controller.authHls(42L, null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void returns_403_for_path_without_userId() {
        ResponseEntity<Void> r = controller.authHls(42L, "/hls/abc/playlist.m3u8");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
