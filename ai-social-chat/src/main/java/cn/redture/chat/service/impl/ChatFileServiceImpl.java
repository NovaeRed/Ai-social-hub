package cn.redture.chat.service.impl;

import cn.redture.chat.mapper.ChatFileMapper;
import cn.redture.chat.mapper.ConversationMapper;
import cn.redture.chat.mapper.ConversationMemberMapper;
import cn.redture.chat.pojo.entity.ChatFile;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.entity.ConversationMember;
import cn.redture.chat.pojo.vo.ChatFileVO;
import cn.redture.chat.service.ChatFileService;
import cn.redture.common.exception.businessException.AccessDeniedException;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatFileServiceImpl implements ChatFileService {

    private static final long MAX_FILE_SIZE_BYTES = 1024L * 1024 * 1024;
    private static final int MAX_BATCH_QUERY_SIZE = 200;

    @Resource
    private ChatFileMapper chatFileMapper;

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private ConversationMemberMapper conversationMemberMapper;

    @Value("${chat.file.upload-dir:./data/chat-files}")
    private String uploadDir;

    @Value("${chat.file.public-base-url:}")
    private String publicBaseUrl;

    @Override
    @Transactional
    public ChatFileVO uploadFile(MultipartFile file, String conversationPublicId, Long uploaderUserId) {
        validateUploadFile(file);

        Long conversationId = resolveConversationIdForUpload(conversationPublicId, uploaderUserId);
        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        String fileExt = extractFileExt(originalFilename);
        String publicId = "file_" + IdUtil.nextId();

        Path targetPath = resolveTargetPath(publicId, fileExt);
        writeFile(file, targetPath);

        ChatFile chatFile = new ChatFile();
        chatFile.setPublicId(publicId);
        chatFile.setUploaderId(uploaderUserId);
        chatFile.setConversationId(conversationId);
        chatFile.setOriginalFilename(originalFilename);
        chatFile.setFileExt(fileExt);
        chatFile.setContentType(resolveContentType(file.getContentType()));
        chatFile.setSizeBytes(file.getSize());
        chatFile.setAccessUrl(buildAccessUrl(publicId));
        chatFile.setCreatedAt(OffsetDateTime.now());

        chatFileMapper.insert(chatFile);
        return toChatFileVO(chatFile);
    }

    @Override
    public ChatFileVO getFileMetadata(String filePublicId, Long currentUserId) {
        ChatFile chatFile = getAuthorizedFile(filePublicId, currentUserId);
        return toChatFileVO(chatFile);
    }

    @Override
    public List<ChatFileVO> getFilesMetadataBatch(List<String> filePublicIds, Long currentUserId) {
        if (filePublicIds == null || filePublicIds.isEmpty()) {
            return List.of();
        }

        List<String> normalizedIds = filePublicIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        if (normalizedIds.size() > MAX_BATCH_QUERY_SIZE) {
            throw new InvalidInputException("批量查询数量超限，最大支持" + MAX_BATCH_QUERY_SIZE + "个文件");
        }

        List<ChatFile> chatFiles = chatFileMapper.selectList(new LambdaQueryWrapper<ChatFile>()
                .in(ChatFile::getPublicId, normalizedIds)
                .isNull(ChatFile::getDeletedAt));

        Map<String, ChatFile> fileMap = chatFiles.stream()
                .collect(Collectors.toMap(ChatFile::getPublicId, f -> f, (a, b) -> a, HashMap::new));

        List<ChatFileVO> result = new ArrayList<>();
        for (String filePublicId : normalizedIds) {
            ChatFile chatFile = fileMap.get(filePublicId);
            if (chatFile == null) {
                continue;
            }
            validateReadPermission(chatFile, currentUserId);
            result.add(toChatFileVO(chatFile));
        }
        return result;
    }

    @Override
    public ChatFile getAuthorizedFile(String filePublicId, Long currentUserId) {
        ChatFile chatFile = requireActiveFile(filePublicId);
        validateReadPermission(chatFile, currentUserId);
        return chatFile;
    }

    @Override
    public org.springframework.core.io.Resource loadFileContent(ChatFile chatFile) {
        Path filePath = resolveTargetPath(chatFile.getPublicId(), chatFile.getFileExt());
        FileSystemResource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            throw new ResourceNotFoundException("文件内容不存在");
        }
        return resource;
    }

    @Override
    public ChatFile getFileForMessage(String filePublicId, Long conversationId, Long senderUserId) {
        ChatFile chatFile = requireActiveFile(filePublicId);
        validateReadPermission(chatFile, senderUserId);

        if (chatFile.getConversationId() != null && !Objects.equals(chatFile.getConversationId(), conversationId)) {
            throw new InvalidInputException("文件不属于当前会话");
        }
        return chatFile;
    }

    @Override
    public List<ChatFile> getActiveFilesByIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return chatFileMapper.selectList(new LambdaQueryWrapper<ChatFile>()
                .in(ChatFile::getId, fileIds)
                .isNull(ChatFile::getDeletedAt));
    }

    private ChatFile requireActiveFile(String filePublicId) {
        if (!StringUtils.hasText(filePublicId)) {
            throw new InvalidInputException("file_public_id 不能为空");
        }
        ChatFile chatFile = chatFileMapper.selectActiveByPublicId(filePublicId);
        if (chatFile == null) {
            throw new ResourceNotFoundException("文件不存在");
        }
        return chatFile;
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidInputException("文件大小超限，最大支持" + MAX_FILE_SIZE_BYTES + "字节");
        }
    }

    private Long resolveConversationIdForUpload(String conversationPublicId, Long uploaderUserId) {
        if (!StringUtils.hasText(conversationPublicId)) {
            return null;
        }
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPublicId, conversationPublicId));
        if (conversation == null) {
            throw new ResourceNotFoundException("会话不存在");
        }
        ConversationMember member = conversationMemberMapper.selectByConversationIdAndUserId(conversation.getId(), uploaderUserId);
        if (member == null) {
            throw new AccessDeniedException("无权在该会话上传文件");
        }
        return conversation.getId();
    }

    private void validateReadPermission(ChatFile chatFile, Long currentUserId) {
        if (chatFile.getConversationId() != null) {
            ConversationMember member = conversationMemberMapper.selectByConversationIdAndUserId(chatFile.getConversationId(), currentUserId);
            if (member == null) {
                throw new AccessDeniedException("无权访问该文件");
            }
            return;
        }
        if (!Objects.equals(chatFile.getUploaderId(), currentUserId)) {
            throw new AccessDeniedException("无权访问该文件");
        }
    }

    // 暂时采用本地文件方式
    private Path resolveTargetPath(String publicId, String fileExt) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(publicId + "." + fileExt).normalize();
        if (!target.startsWith(root)) {
            throw new InvalidInputException("文件路径非法");
        }
        return target;
    }

    // TODO　为了防止大文件超时导致重传而浪费服务器带宽，后续采用分片＋缓存的方式上传文件
    private void writeFile(MultipartFile file, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException e) {
            log.error("上传文件写入失败: {}", targetPath, e);
            throw new InvalidInputException("文件上传失败，请稍后重试");
        }
    }

    private String buildAccessUrl(String publicId) {
        String path = "/files/" + publicId + "/content";
        if (!StringUtils.hasText(publicBaseUrl)) {
            return path;
        }
        String normalizedBase = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return normalizedBase + path;
    }

    // 此处暂时采用本地文件的获取方式
    private String normalizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "file.bin";
        }
        try {
            String cleaned = Paths.get(originalFilename).getFileName().toString();
            return StringUtils.hasText(cleaned) ? cleaned : "file.bin";
        } catch (InvalidPathException e) {
            return "file.bin";
        }
    }

    private String extractFileExt(String originalFilename) {
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == originalFilename.length() - 1) {
            return "bin";
        }
        String ext = originalFilename.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        ext = ext.replaceAll("[^a-z0-9]", "");
        if (!StringUtils.hasText(ext)) {
            return "bin";
        }
        return ext.length() > 20 ? ext.substring(0, 20) : ext;
    }

    private String resolveContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private ChatFileVO toChatFileVO(ChatFile chatFile) {
        ChatFileVO vo = new ChatFileVO();
        vo.setPublicId(chatFile.getPublicId());
        vo.setOriginalFilename(chatFile.getOriginalFilename());
        vo.setContentType(chatFile.getContentType());
        vo.setSizeBytes(chatFile.getSizeBytes());
        vo.setAccessUrl(chatFile.getAccessUrl());
        vo.setCreatedAt(chatFile.getCreatedAt());
        return vo;
    }
}
