package com.mangotv.app.data.network

/**
 * A real HTTP error response the server sent back — as opposed to a
 * network-level failure (timeout, no connectivity, DNS), which surfaces
 * as a plain IOException instead. Callers rely on this distinction: a
 * confirmed 401 means "the server says this credential is dead", not
 * "we couldn't reach it" — and only the former should ever clear a
 * locally stored session.
 */
class ApiException(val statusCode: Int, message: String) : Exception(message)
