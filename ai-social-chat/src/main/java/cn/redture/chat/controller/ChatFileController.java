package cn.redture.chat.controller;

import cn.redture.chat.pojo.dto.FileMetadataBatchQueryDTO;
import cn.redture.chat.pojo.entity.ChatFile;
import cn.redture.chat.pojo.vo.ChatFileVO;
import cn.redture.chat.service.ChatFileService;
import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import jakarta.annotation.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/files")
public class ChatFileController {

    @Resource
    private ChatFileService chatFileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestResult<ChatFileVO> uploadFile(@RequestPart("file") MultipartFile file,
                                             @RequestParam(value = "conversation_public_id", required = false) String conversationPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        ChatFileVO chatFile = chatFileService.uploadFile(file, conversationPublicId, currentUserId);
        return RestResult.created(chatFile);
    }

    @GetMapping("/{file_public_id}")
    public RestResult<ChatFileVO> getFileMetadata(@PathVariable("file_public_id") String filePublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        ChatFileVO chatFile = chatFileService.getFileMetadata(filePublicId, currentUserId);
        return RestResult.success(chatFile);
    }

    @PostMapping("/metadata/batch")
    public RestResult<List<ChatFileVO>> getFilesMetadataBatch(@RequestBody FileMetadataBatchQueryDTO dto) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        List<ChatFileVO> files = chatFileService.getFilesMetadataBatch(dto.getFilePublicIds(), currentUserId);
        return RestResult.success(files);
    }

    @GetMapping("/{file_public_id}/content")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(@PathVariable("file_public_id") String filePublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        ChatFile chatFile = chatFileService.getAuthorizedFile(filePublicId, currentUserId);
        org.springframework.core.io.Resource resource = chatFileService.loadFileContent(chatFile);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (chatFile.getContentType() != null && !chatFile.getContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(chatFile.getContentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(chatFile.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }
}
