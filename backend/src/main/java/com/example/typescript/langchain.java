package com.example.typescript;

import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class langchain {
    private static final Logger logger = Logger.getLogger(langchain.class.getName());
    //LLM
    public static OllamaChatModel model = OllamaChatModel.builder()
            .modelName("huihui_ai/deepseek-r1-abliterated:14b")
            .baseUrl("http://localhost:11434")
            .temperature(0.7)
            .build();

    public String langchain4j(String workSpace) {

        File repoDir = new File(workSpace);

        List<String> modifiedFiles = getModifiedFiles(repoDir);



        if (modifiedFiles.isEmpty()) {
            System.out.println("沒有檔案變更，無需產生 commit message。");
            return "沒有檔案變更，無需產生 commit message。";
        }

        StringBuilder allDiffResults = new StringBuilder();

        for (String file : modifiedFiles) {
            StringBuilder diffResults = new StringBuilder();
            diffResults.append(getGitDiff(repoDir, file)).append("\n");
            String prompt1 = """
                請基於以下 Conventional Commits 規範生成一個簡單的英文 Commit Message：
                請直接輸出一行符合 Conventional Commits 規範的英文 commit message
                1. `feat` 用於新增功能，`fix` 用於修復 bug，`docs` 用於文件變更，`refactor` 用於重構，`chore` 用於開發工具變更。
                2. Commit Message 格式範例如下：

                   feat: 簡單描述哪個檔案增加了那些功能

                """ + diffResults;
            //對所有修改檔各自生成一個簡易的massege，並整合成一個字串
            String raw = model.generate(prompt1);
            String simpleMessage = cleanMessage(raw);
            System.out.println(simpleMessage);
            allDiffResults.append(simpleMessage).append("\n");

        }

        //將所有的simple massege整合成一個完整的commit massege
        String prompt2 = """
                請閱讀以下多段 commit message，並依照 Conventional Commits 規範，整合為一個清楚且有組織的 commit message。若有相同類型 (如 feat, fix)，請合併為一段，並將細節列成清單。
                如有多種類型，每種類型的message都要輸出。
                都用英文輸出。
                請只輸出最終 commit message，格式如下：
                範例如下：

                   feat: 增加功能
                   - 哪個檔案增加了什麼功能
                   - 哪個檔案增加了什麼功能
                   
                   refactor: 重構功能
                   - 哪個檔案重構了什麼功能
                   - 哪個檔案重構了什麼功能

                你是一個負責生成 Git Commit Message 的 AI，請輸出符合 Conventional Commits 規範的英文 Commit Message：
                """ + allDiffResults;

        String commitMessage = model.generate(prompt2);
        System.out.println(commitMessage);
        return cleanMessage(commitMessage);
    }


    /*
    **獲取有哪些修改檔，進行git diff，並將整合後的diff info回傳給langchain Function
     */
    private static List<String> getModifiedFiles(File repoDir) {
        List<String> modifiedFiles = new ArrayList<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("git", "status", "--porcelain");
            processBuilder.directory(repoDir);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    // 處理修改 (M) 和 移動 (RM) 的情況
                    if (line.matches("^(M|RM|AM).*\\.java")) {
                        // 抓路徑部分：從第4個字元開始
                        String path = line.substring(3).trim();

                        if (path.contains("->")) {
                            // 處理移動（RM），只取箭頭右邊的新路徑
                            String[] parts = path.split("->");
                            if (parts.length == 2) {
                                String newPath = parts[1].trim();
                                modifiedFiles.add(newPath);
                            }
                        } else {
                            modifiedFiles.add(path);
                        }
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return modifiedFiles;
    }


    /*
    **執行git diff
     */
    private static String getGitDiff(File repoDir, String file) {
        StringBuilder output = new StringBuilder();
        try {
            // 執行 `git diff` 指令
            ProcessBuilder processBuilder = new ProcessBuilder("git", "diff", file);
            processBuilder.directory(repoDir);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return output.toString();
    }


    /*
    **取得GIT status訊息並回傳給前端
     */
    public String getGitStatus(String workSpace) {
        File repoDir = new File(workSpace);
        StringBuilder statusOutput = new StringBuilder();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("git", "status");
            processBuilder.directory(repoDir);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    statusOutput.append(line).append("\n");
                }
            }

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            return "取得 git status 時發生錯誤：" + e.getMessage();
        }

        String statusPrompt = """
                根據下列 `git status` 的原始輸出，請將檔案依照變動類型進行分類，並用清楚的人類可讀格式輸出，並加入檔案數量，如果有數目為0的類型也要輸出，使用英文。
                  請分類成四個區塊：
                    1. 內容修改過的檔案
                    2. 新增的檔案或資料夾 
                    3. 刪除的檔案或資料夾
                    4. 變更過路徑的檔案
                
                    格式範例如下：
                
                    modified(2 files):
                     - src/App.java
                     - src/utils/Helper.java
    
                    add(1 files):
                     - src/newmodule/NewService.java
    
                    delete(1 files):
                     - src/oldmodule/OldService.java
    
                    renamed(1 files):
                     - src/Animal/Buff.java -> src/Function/Buff.java
    
                     以下是 git status 的結果，請根據規則進行整理，不需要補充或解釋，只要乾淨列出結果，不要有其他文字輸出，請按照上述的順序輸出類型，每個類型之間用一行空白行隔開。
                """ + statusOutput;
        String raw = model.generate(statusPrompt);
        return cleanMessage(raw);
    }



    /*
    **將模型的推理過程清除
     */
    private static String cleanMessage(String rawOutput) {
        return rawOutput
                .replaceAll("(?s)<think>.*?</think>", "")
                .trim();
    }
}
