package kdt.washPrj.nonMember;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NonMemberAccessMapper {

    @Select("""
        SELECT app_api.non_member_plate_exists(
            CAST(#{plateNumber} AS varchar)
        )
    """)
    boolean existsNonMemberPlateNumber(@Param("plateNumber") String plateNumber);

    @Select("""
        SELECT app_api.get_non_member_plate(
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    String selectNonMemberPlateNumber(@Param("washHistoryId") Long washHistoryId);
}
