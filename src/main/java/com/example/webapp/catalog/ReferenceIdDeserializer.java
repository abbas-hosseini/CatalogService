package com.example.webapp.catalog;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Never truncate a decimal into the id of an unrelated reference row. */
public class ReferenceIdDeserializer extends ValueDeserializer<Long> {
    @Override public Long deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return parser.getLongValue();
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            String value = parser.getString().strip();
            if (value.matches("-?[0-9]+")) {
                try { return Long.valueOf(value); } catch (NumberFormatException ignored) { }
            }
        }
        throw new CatalogException(400, "تخصص و سطح سازمانی را از گزینه‌های معتبر انتخاب کنید؛ شناسه باید عدد صحیح باشد.");
    }
}
