package kdt.washPrj.global.security;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserMapperContractTest {

    @Test
    void actorLookupAcceptsLoginIdAndRole() throws Exception {
        Method method = CurrentUserMapper.class.getMethod(
                "findActorPk",
                String.class,
                String.class
        );

        assertEquals(Integer.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void actorLookupUsesAppApiFunction() throws Exception {
        Select select = CurrentUserMapper.class
                .getMethod("findActorPk", String.class, String.class)
                .getAnnotation(Select.class);

        assertTrue(String.join(" ", select.value())
                .contains("app_api.get_actor_pk"));
    }
}
