package com.mangotv.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordSignInViewModelTest {

    @Test
    fun `blank email is rejected`() {
        assertEquals("Enter your email address.", validateCredentials("", "password123"))
    }

    @Test
    fun `whitespace-only email is rejected`() {
        assertEquals("Enter your email address.", validateCredentials("   ", "password123"))
    }

    @Test
    fun `email missing an at sign is rejected`() {
        assertEquals("Enter a valid email address.", validateCredentials("not-an-email", "password123"))
    }

    @Test
    fun `email missing a domain dot is rejected`() {
        assertEquals("Enter a valid email address.", validateCredentials("a@b", "password123"))
    }

    @Test
    fun `email with embedded whitespace is rejected`() {
        assertEquals("Enter a valid email address.", validateCredentials("a b@example.com", "password123"))
    }

    @Test
    fun `surrounding whitespace on an otherwise valid email is trimmed`() {
        assertNull(validateCredentials("  a@example.com  ", "password123"))
    }

    @Test
    fun `password below the minimum length is rejected`() {
        assertEquals(
            "Password must be at least 8 characters.",
            validateCredentials("a@example.com", "short1")
        )
    }

    @Test
    fun `password one character below the minimum is rejected`() {
        assertEquals(
            "Password must be at least 8 characters.",
            validateCredentials("a@example.com", "1234567")
        )
    }

    @Test
    fun `password exactly at the minimum length is accepted`() {
        assertNull(validateCredentials("a@example.com", "12345678"))
    }

    @Test
    fun `valid email and password produce no error`() {
        assertNull(validateCredentials("user@example.com", "password123"))
    }

    @Test
    fun `email is validated before password`() {
        assertEquals("Enter a valid email address.", validateCredentials("not-an-email", "short"))
    }
}
