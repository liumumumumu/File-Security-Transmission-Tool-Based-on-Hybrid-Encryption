package com.filesecuritytool.android.crypto

import com.filesecuritytool.android.core.crypto.RsaOaep
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

class JavaInteropVectorTest {
    private val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
        PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_PKCS8_BASE64))
    )
    private val service = OfflineCryptoService(
        rsaEncryptor = { _, _ -> error("Not used by Java-vector decryption") },
        rsaDecryptor = {
            SecretKeySpec(
                RsaOaep.decrypt(Base64.getDecoder().decode(it), privateKey),
                "AES"
            )
        }
    )

    @Test
    fun `decrypts fixed FST2 vector produced by Java reference`() {
        val output = ByteArrayOutputStream()
        val result = service.decryptFile(
            ByteArrayInputStream(Base64.getDecoder().decode(JAVA_FST2_BASE64)),
            output
        )
        assertEquals("java-vector.txt", result.fileName)
        assertEquals(
            "Java to Android FST2 interoperability vector\\n第二行",
            output.toString(Charsets.UTF_8)
        )
    }

    @Test
    fun `decrypts fixed FST TEXT1 vector produced by Java reference`() {
        assertEquals(
            "Java to Android FST-TEXT1 互操作",
            service.decryptText(JAVA_FST_TEXT1).text
        )
    }

    @Test
    fun `retains Android FST2 vector verified by Java reference`() {
        val output = ByteArrayOutputStream()
        service.decryptFile(
            ByteArrayInputStream(Base64.getDecoder().decode(ANDROID_FST2_BASE64)),
            output
        )
        assertEquals("Android to Java FST2 interoperability vector", output.toString(Charsets.UTF_8))
    }

    @Test
    fun `retains Android FST TEXT1 vector verified by Java reference`() {
        assertEquals(
            "Android to Java FST-TEXT1 互操作",
            service.decryptText(ANDROID_FST_TEXT1).text
        )
    }

    companion object {
        private const val PRIVATE_PKCS8_BASE64 =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDR2PVNNSTAOIutc5a55CPXbh/A/M0VneOt9Jtsz5QaNxiKZ2ASyILUuH5OpOPXHzqhlX8G3IktQmWCKGbm5A0CAaFSVm47tjM+QakLiu+eT07dlRI3sKBdhyumoCFpt8p/8y9F22LojZSs6a/nGfWJyPcpag5U7W/HvQ6AXf8fgM2Bfb0v+VotLQ/cbaGF/THdqYcF0ajCNyn1abAyBY5p49WW5xMtEul0PCCKEu7rSCSgJYM1FvUvRaNu/F3JMFDCVDlWbiaoJDPlrL+LnyrOYHbPSzSAX6oVqEHGwrMeFNjOmYCmvsRoBBKhCnWvuspjFZLP77umjcijZt+aGvP7AgMBAAECggEAMa1NQNZMWW7pbgnXjDEcZQLuZHbULAJhJEysQKGRW3Vgi182iKvMAQSd7gst3r7HV/o2hI7PsJWvxxS+a3lgNPHi2UWZuewDjIDpvlBJMm5u6pjAI8vd4tY9eKge0NKQDyMoNT7e1tOxdw+pMynbjR80l6rCMsu0sxGn7a6S10duMtbvXVqBw3+8PIZYzDg97bg4C5M3y5y2znipkBOEzIgCQTzTkMN3dqccrshbx4datDQBqwA/bdzbAQpk0lx8EZNwCejGShrPD1Z7JqDW4Q0PFY+asrVF2iH+SDk14ARRgl6lUyWBXW5AcgK0axkmCNeJf5s+f4wa7uR80ioBiQKBgQD1lDNhXONeAoVX3RnFtdtscCAg1d0Mnwi0whE18kxsDz+bwiEInqVnvYzcKczif/3pQSDyUD9xvLGQo1jqewBJ+zYIorqGkczCcwu84JptZifuBz3pTOhhReElhq9QSgVKvDr4jZlSYfN581oyPkudit5ghV7+auumojTOVl65aQKBgQDawJicH3x4CYev3+8CY/o7rCeHeP3RxpZyT1TQVlxccaWKjoVDRIud4NnVVh3knwzpWGOrfBJ0gR57whHb25woG7NL/yM9shTtF+H5TVYDj8YmNmwFG/P0VGNddRvsbIQGUURw2ihdp5aTaRFjQF5ND/ZG8IRg4cspgRgARk3RwwKBgDGa6/4Au1SkAbdLzAbpwxbWV0cKrAn+nc2VXdCdzt4M/nZB8lZBZXvdB/T45I/j9rAFHdiUaRxaVyu2MyG6Esbe3X1FEZRZIxksD/TpEYiDmBE2mUTk6hOr/9yKpiL7sLH175uBTrUbYAkEJPX9K1z5Tg4FIrkVc+BG54++VofxAoGBAI56r2vQ3kNZAbW+B3DpKY1AXibQ2ZGO5WLi5P0lotbbdtU+avIbbUyRrZBUnPfR2JkL5y9EisizaOV2zCcFbTp6gFfZgFyo5xReX+L/SizbslYlUEoroMPdSzMOGfft3jg3KnrOvUfy5hJxds/Mlx9ebCxcb/yiVK9d1AIpyHn9AoGAXhiTkROZupYyzyv6Y9yK7ZRFmHZ6Kch25fJ1l2gjUb2orz1B/UvZUzpWHDhDbDJulx49oPeYC/S5xFYrgZn8ppFDAJ8PPJBpCnyfD9vEOHADr9FWkBB8lvHucwaROSGvG/COLNX9JgKZ2GhqSh30fMEOZyhJKALltivSHEBmeMc="
        private const val JAVA_FST2_BASE64 =
            "RlNUMgEBAQEAAAEAN9B9I7ER6nt/9x9LpOWUQXjOgQwLeQZhnZhfWiA9lHuE5F7piUhWNegKHFVMSoLO1nmx2NJCQnGAyT0G/8TpN6s3DLwS1rPz3M1/WrbjbytaOSOR7jjdLWHSRKpQ0gFNtFTU+WJyeq0Ek54zaVuvDWoZ4NuH/dzJjrFzr7wropL0x33MuUBudWCK70fHUvP/yWbnrLwqzChUS+cLbKb1QPkuB5WqRHrRHHWv/oKEKjKrXM0xTzCbM0f9ZAOJLc2g8dCwReuZDZVl0zBiMfsuQmKI6//3am7dl6rCJpsiW+SGBzhj5f7kTjYflYvwFQYHtZdmilMvCQccrA/xdC/yjQAAACCTOReZ5izTFTmUh23GyTCi8sz7ptbLl3qqcAryCGNUCQAAAEYHw9eWUj0ef3WvdVtx1HxEXybhYimdwsMVhALdftq6fd4KjnkjcD+NqYDSVGoW0gth6MtT8+2v9xrLvtxpD9WCgTkSU2KsAAAAEESeKZEeH1VyOlTacZow07wAAAAAAAAANwAAADfolJGpVa78qCLtA2EMWnVeGoGR1s/rJ29Ey9LA2XGPQNM30tBLUbRT9c34tL/mJBOG9mIKjumgAAAAEOLL7ZaBWYoWm16lQDztUBc="
        private const val JAVA_FST_TEXT1 =
            "FST-TEXT1:qGN0YWdQWyfwf4a4kmDIQfWoOEIAnWVub25jZUyL6h1SSnMT1HVFiBpndmVyc2lvbgFqY2lwaGVydGV4dFgjbSBHJ4ccRjmtaJn1kuUdw1P1w7MAR68mbWmF090_o8dmhxpqY29udGVudEFsZwFqa2V5V3JhcEFsZwFvcGxhaW50ZXh0TGVuZ3RoGCNzZW5jcnlwdGVkU2Vzc2lvbktleVkBAFIjKL1FQDorC8xUnA7KXsj5yLRY_FdNCtNPo4TYkvtEisbvDTIbXqzm1pwcIu1HQ0ozgIl-87ezmJ32fov7lpj-9sUR9lL5NpnwseXMc0k8Lkwscg_WVAsCvcxswgS4X-CEkRN6UWGIwWn_uPkc9-Eq6D8hoP_kYX6mQOo6aBlUwLKgIrFi3RYUGypg-LUb4QLulAKmAUqXbYzzE9ePoAWWtl10N1P28kYlt8IzkdL-t8DAZvVJMgWDGVKdRaM8gOMgjOoXXEG3GPWbJoKQW9nGp7zHOFN9TuXEKbBfTapuiGQkh_PUvqITKX-uwqN1RaD_YJi9-L82Fl3YCYAPxcY"
        private const val ANDROID_FST2_BASE64 =
            "RlNUMgEBAQEAAAEAbA/AKCPCYwran8qcJ/wFh0phcDxhI4pIOqNmk6h0JZzv5P3iMGcdwb7jLUpcJRVzwwu9psQX+YBFkT3xVcaugzNoQVw/+cSqNkGExriNZDTUCzkONP3mmAfilOzG6/dWXAVug8mf8vmQq7+giFWB6aV6LxDG67m+PHe2tsJqiz+DfoxM7P2jVTJ/Nt2Ufb2sUovXriZgm/3oEb/+SjwI32CmnTrFOmn5qOture0gVnRM7dErJu6N/Dx6RWsYrmFRi7W8fMF0fBuxMRO55Cs30jGregkFUbHKN0OHQaXYxciecAHDZiY7dp3I9NWwoAT7gqDJrbs7cWFTqVh21t4/IgAAACCgeaJ7At/r+gRZZPXw+QuCaHs38IJouIKMn/oqk7NDJQAAAEmBH16c++kj6hWFF9YsRZztU6L/L/XuEfG9zEZLMzB52UIb7hU0r+7fhdcYCcFXEB1bkNGXeRrBDQlvwCq4DuAKudGETvljcQyyAAAAEMLunr5kYGn5ttZZ6arJZ+cAAAAAAAAALAAAACxcgtyi7mhZjJNaLGCpxzpaspT9WX5mgsoIY1zdcqsL0wsbOLYZ5UaWMc4o8QAAABDjngRlNgZpsLxcVJFHWN4y"
        private const val ANDROID_FST_TEXT1 =
            "FST-TEXT1:qGN0YWdQ1bT8y5N0FrOswXMP914HeWVub25jZUwAn2TfcrmMOzq73MlndmVyc2lvbgFqY2lwaGVydGV4dFgjj2f3sqkRWu54PdJ8v3hspzXTN25zV112d7VMduJ_dxkaskhqY29udGVudEFsZwFqa2V5V3JhcEFsZwFvcGxhaW50ZXh0TGVuZ3RoGCNzZW5jcnlwdGVkU2Vzc2lvbktleVkBADdM8UaFGV1EYjZM1ohQo3VYXDAxQp9hwlBdTbDV0E5uLjkgSFWCBImKEkwf1weYN4LObkm64NoekylAAUwFl_kdNtQ_ZMtBEHi-b40pXy3ofholU4oQE-P9EdgUEK8Ck_T4A268Ox97893Whn_l2WzmL44gholMtMz-isM0vMVfvCVlTuzlUn-IKAKIr3Yc-FOs3_IGIslEZxwClvaZY4k3_BoAgrhP1Jv_b4rQsjUI0N3MzJ9GqyTv_6ZJ142PWvGpzV8urT9KLrCnqMNQOoxiuAtpjtb8Y8jWyMbLbW7PiWWn9-ydZBRcgFFFQ4hlWqPwDnWHcW1qshGa6jJofys"
    }
}
