package com.openzeekr.app.net.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseResponseTest {

    @Test
    fun theSuccessFlagOrTheStandardCodeIsOk() {
        assertTrue(BaseResponse<Unit>(success = true, code = "123").isOk)
        assertTrue(BaseResponse<Unit>(success = false, code = "000000").isOk)
        assertTrue(BaseResponse<Unit>(success = false, code = null).isOk)
    }

    // Regression: commands only checked `data`, so an HTTP-200 envelope carrying a business error
    // (e.g. 037005 "execution failed") plus a data object read as success.
    @Test
    fun aBusinessErrorCodeIsNotOkEvenWithData() {
        val r = BaseResponse(success = false, code = "037005", msg = "execution failed", data = "x")
        assertFalse(r.isOk)
        assertEquals("execution failed", r.errorText("command failed"))
    }

    @Test
    fun errorTextFallsBackToTheCode() {
        assertEquals("command failed (code=3000013)", BaseResponse<Unit>(code = "3000013").errorText("command failed"))
    }
}
