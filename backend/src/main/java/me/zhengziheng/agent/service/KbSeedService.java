package me.zhengziheng.agent.service;

import me.zhengziheng.agent.entity.Document;
import me.zhengziheng.agent.mapper.DocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 知识库 seed：应用启动、数据库就绪后，把 kb/*.md 幂等灌入 PGVector。
 * 幂等策略：文件名 + SHA-256(content_hash) 一致则跳过；同名但内容变化则删除旧文档（级联删 chunk）后重灌。
 * 支持外部目录：KB_DIR 指向外部绝对路径时扫描该目录（改内容重启即生效，无需重新打包）。
 */
@Service
public class KbSeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KbSeedService.class);

    private final DocumentMapper documentMapper;
    private final DocumentIngestService ingestService;

    @Value("${rag.kb.dir:classpath:kb}")
    private String kbDir;

    private final AtomicReference<LocalDateTime> lastSeedAt = new AtomicReference<>();

    public KbSeedService(DocumentMapper documentMapper,
                         DocumentIngestService ingestService) {
        this.documentMapper = documentMapper;
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            rebuild();
        } catch (Exception e) {
            // seed 失败不阻断启动：聊天接口会返回"知识库尚未就绪"，管理页可查看状态并手动重建
            log.error("知识库 seed 失败（服务继续启动）：{}", e.getMessage(), e);
        }
    }

    /** 重建知识库：扫描 kb 目录，逐文件按 hash 幂等灌入 */
    @Transactional
    public synchronized KbStatus rebuild() {
        KbStatus status = new KbStatus();
        status.files = new ArrayList<>();
        int seeded = 0;
        int skipped = 0;
        try {
            List<KbFile> files = scanKbFiles();
            for (KbFile f : files) {
                String hash = sha256(f.content);
                Document existing = documentMapper.selectByName(f.name);
                if (existing != null && hash.equals(existing.getContentHash())) {
                    skipped++;
                    status.files.add(f.name + " (跳过,未变)");
                    continue;
                }
                if (existing != null) {
                    // 内容变化：删旧文档（外键 ON DELETE CASCADE 级联删除其 chunk）后重灌
                    documentMapper.deleteByDocId(existing.getDocId());
                }
                ingestService.ingestContent(f.name, f.content);
                seeded++;
                status.files.add(f.name + " (已灌入)");
            }
            status.seeded = seeded;
            status.skipped = skipped;
            status.docCount = documentMapper.countAll();
            lastSeedAt.set(LocalDateTime.now());
            log.info("知识库 seed 完成：灌入 {} 篇，跳过 {} 篇，共 {} 篇文档", seeded, skipped, status.docCount);
        } catch (Exception e) {
            log.error("知识库重建失败：{}", e.getMessage(), e);
            status.error = e.getMessage();
        }
        return status;
    }

    /** 扫描 kb 目录：支持 classpath:kb 或外部绝对路径 */
    private List<KbFile> scanKbFiles() throws Exception {
        List<KbFile> out = new ArrayList<>();
        if (kbDir != null && !kbDir.startsWith("classpath:")) {
            // 外部目录
            File dir = new File(kbDir);
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".md"));
                if (files != null) {
                    for (File f : files) {
                        String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                        out.add(new KbFile(f.getName(), content));
                    }
                }
            }
            return out;
        }
        // classpath
        String pattern = kbDir.startsWith("classpath:") ? kbDir.substring("classpath:".length()) : kbDir;
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:" + pattern + "/*.md");
        for (Resource r : resources) {
            String name = r.getFilename();
            String content = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            out.add(new KbFile(name, content));
        }
        return out;
    }

    /** 知识库状态（管理页查看） */
    public KbStatus status() {
        KbStatus s = new KbStatus();
        s.docCount = documentMapper.countAll();
        s.lastSeedAt = lastSeedAt.get();
        return s;
    }

    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(content.hashCode());
        }
    }

    private static class KbFile {
        final String name;
        final String content;

        KbFile(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }

    /** 知识库状态 VO */
    public static class KbStatus {
        public int seeded;
        public int skipped;
        public long docCount;
        public LocalDateTime lastSeedAt;
        public String error;
        public List<String> files;
    }
}
