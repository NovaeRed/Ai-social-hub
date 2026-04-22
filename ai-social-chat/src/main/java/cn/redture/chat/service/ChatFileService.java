package cn.redture.chat.service;

import cn.redture.chat.pojo.entity.ChatFile;
import cn.redture.chat.pojo.vo.ChatFileVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChatFileService {

    ChatFileVO uploadFile(MultipartFile file, String conversationPublicId, Long uploaderUserId);

    ChatFileVO getFileMetadata(String filePublicId, Long currentUserId);

    List<ChatFileVO> getFilesMetadataBatch(List<String> filePublicIds, Long currentUserId);

    ChatFile getAuthorizedFile(String filePublicId, Long currentUserId);

    Resource loadFileContent(ChatFile chatFile);

    ChatFile getFileForMessage(String filePublicId, Long conversationId, Long senderUserId);

    List<ChatFile> getActiveFilesByIds(List<Long> fileIds);
}
