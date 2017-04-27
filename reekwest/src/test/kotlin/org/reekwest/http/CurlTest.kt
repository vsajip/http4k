package org.reekwest.http

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import org.reekwest.http.core.body
import org.reekwest.http.core.body.bodyString
import org.reekwest.http.core.body.toBody
import org.reekwest.http.core.get
import org.reekwest.http.core.header
import org.reekwest.http.core.query
import org.reekwest.http.core.toCurl

class CurlTest {

    @Test
    fun `generates for simple get request`() {
        val curl = get("http://www.google.com").toCurl()
        assertThat(curl, equalTo("curl -X GET \"http://www.google.com\""))
    }

    @Test
    fun `generates for request with query`() {
        val curl = get("http://www.google.com").query("a", "one two three").toCurl()
        assertThat(curl, equalTo("curl -X GET \"http://www.google.com?a=one+two+three\""))
    }

    @Test
    fun `includes headers`() {
        val curl = get("http://www.google.com").header("foo", "my header").toCurl()
        assertThat(curl, equalTo("curl -X GET -H \"foo:my header\" \"http://www.google.com\""))
    }

    @Test
    fun `includes body data`() {
        val curl = get("http://www.google.com").body(listOf("foo" to "bar").toBody()).toCurl()
        assertThat(curl, equalTo("curl -X GET --data \"foo=bar\" \"http://www.google.com\""))
    }

    @Test
    fun `escapes body form`() {
        val curl = get("http://www.google.com").body(listOf("foo" to "bar \"quoted\"").toBody()).toCurl()
        assertThat(curl, equalTo("""curl -X GET --data "foo=bar+%22quoted%22" "http://www.google.com""""))
    }

    @Test
    fun `escapes body string`() {
        val curl = get("http://www.google.com").bodyString("my \"quote\"").toCurl()
        assertThat(curl, equalTo("""curl -X GET --data "my \"quote\"" "http://www.google.com""""))
    }
}