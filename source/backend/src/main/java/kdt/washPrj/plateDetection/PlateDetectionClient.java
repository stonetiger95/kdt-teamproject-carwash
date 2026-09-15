package kdt.washPrj.plateDetection;

import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import kdt.washPrj.plateDetection.dto.PlateDetectionRequestDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionResultDTO;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Component
public class PlateDetectionClient {
   @Resource
   RestClient restClient;

   public PlateDetectionResultDTO detection(PlateDetectionRequestDTO request){
      try {
         MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

         body.add("image", new ByteArrayResource(request.getImage().getBytes()) {
            @Override
            public @Nullable String getFilename() {
               return request.getImage().getOriginalFilename();
            }
         });

         PlateDetectionResultDTO detectionRes = restClient.post()
                 .uri("http://localhost:8000/api/plate-recognition")
                 .contentType(MediaType.MULTIPART_FORM_DATA)
                 .body(body)
                 .retrieve()
                 .body(PlateDetectionResultDTO.class);
         return detectionRes;
      } catch (IOException e) {
         return failResult("이미지 파일 읽기 실패: "+e.getMessage());

      } catch (Exception e) {
         return failResult("FastAPI 호출 실패: "+e.getMessage());
      }
   }

   private PlateDetectionResultDTO failResult(String message) {
      PlateDetectionResultDTO result = new PlateDetectionResultDTO();

      result.setDetectedPlateNumber(null);
      result.setConfidence(null);
      result.setSuccess(false);
      result.setMessage(message);

      return result;
   }


}
