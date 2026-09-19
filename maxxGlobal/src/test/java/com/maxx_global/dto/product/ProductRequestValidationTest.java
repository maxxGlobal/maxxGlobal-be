package com.maxx_global.dto.product;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

import static org.junit.jupiter.api.Assertions.*;

class ProductRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankAndWhitespaceEnglishNamesAreNormalizedToNull() throws Exception {
        assertNull(request("").nameEn());
        assertNull(request("   ").nameEn());
    }

    @Test
    void nullEnglishNameIsAccepted() throws Exception {
        ProductRequest request = request(null);
        assertNull(request.nameEn());
        assertTrue(validator.validateProperty(request, "nameEn").isEmpty());
    }

    @Test
    void realEnglishNameIsTrimmedAndStillEnforcesSize() throws Exception {
        ProductRequest tooShort = request(" a ");
        ProductRequest valid = request(" Implant ");

        assertEquals("a", tooShort.nameEn());
        assertFalse(validator.validateProperty(tooShort, "nameEn").isEmpty());
        assertEquals("Implant", valid.nameEn());
        assertTrue(validator.validateProperty(valid, "nameEn").isEmpty());
    }

    private ProductRequest request(String nameEn) throws Exception {
        RecordComponent[] components = ProductRequest.class.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            if (components[i].getName().equals("nameEn")) {
                values[i] = nameEn;
            }
        }
        return ProductRequest.class.getDeclaredConstructor(types).newInstance(values);
    }
}
