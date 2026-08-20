package com.example

import com.example.model.ControlMode
import com.example.model.InterfaceType
import com.example.network.NetworkUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testControlModeEnum() {
    assertEquals("USB_SINGLE_USER", ControlMode.USB_SINGLE_USER.name)
    assertEquals("HOTSPOT_MULTI_USER", ControlMode.HOTSPOT_MULTI_USER.name)
    assertEquals(2, ControlMode.values().size)
  }

  @Test
  fun testFormatBytes() {
    assertEquals("0 B", NetworkUtils.formatBytes(0))
    assertEquals("500 B", NetworkUtils.formatBytes(500))
    assertTrue(NetworkUtils.formatBytes(1024).contains("KB"))
    assertTrue(NetworkUtils.formatBytes(1048576).contains("MB"))
    assertTrue(NetworkUtils.formatBytes(1073741824).contains("GB"))
  }
}

