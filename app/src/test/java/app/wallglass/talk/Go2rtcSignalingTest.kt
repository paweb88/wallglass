package app.wallglass.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Go2rtcSignalingTest {
    @Test fun webrtcUrlEncodesStream() {
        assertEquals("http://192.168.1.10:1984/api/webrtc?src=front%20gate", Go2rtcSignaling.url("http://192.168.1.10:1984/", "front gate"))
    }

    @Test fun offerIsJson() {
        assertEquals("""{"type":"offer","sdp":"v=0\r\n"}""", Go2rtcSignaling.offer("v=0\r\n"))
    }

    @Test fun readsAnswer() {
        assertEquals("v=0\r\na=x\r\n", Go2rtcSignaling.answer("""{"type":"answer","sdp":"v=0\r\na=x\r\n"}"""))
    }

    @Test fun rawSdpAnswerAlsoWorks() {
        assertEquals("v=0\r\n", Go2rtcSignaling.answer("v=0\r\n"))
    }

    @Test fun errorIsNotAnAnswer() {
        assertNull(Go2rtcSignaling.answer("streams: stream not found"))
    }
}
