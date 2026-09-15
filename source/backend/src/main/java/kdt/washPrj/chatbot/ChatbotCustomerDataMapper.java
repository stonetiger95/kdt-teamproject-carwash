package kdt.washPrj.chatbot;

import kdt.washPrj.chatbot.dto.ChatbotCustomerBalanceDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatbotCustomerDataMapper {

    @Select("SELECT * FROM app_api.get_chatbot_customer_balance(CAST(#{loginId} AS varchar))")
    ChatbotCustomerBalanceDTO selectCustomerBalance(
            @Param("loginId") String loginId
    );
}
