package com.phpinnacle.redoc;

import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.impl.JsonObjectImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.phpinnacle.redoc.settings.RedocSettings;
import com.phpinnacle.redoc.settings.RedocSettings.ChangeListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLPsiElement;
import org.jetbrains.yaml.psi.YAMLValue;
import org.jetbrains.yaml.psi.impl.YAMLDocumentImpl;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;

import java.util.Objects;

class RedocApplication implements ApplicationComponent {
    private RedocServer server = RedocServer.getInstance();
    private RedocSettings settings = RedocSettings.getInstance();
    private FileDocumentManager manager = FileDocumentManager.getInstance();

    @Override
    public void initComponent() {
        ChangeListener listener = new ChangeListener() {
            @Override
            public void settingsChanged(@NotNull RedocSettings settings) {
            }
        };

        ApplicationManager.getApplication().getMessageBus().connect().subscribe(ChangeListener.TOPIC, listener);
    }

    @Override
    public void disposeComponent() {
        server.dispose();
    }

    boolean isAcceptable(@NotNull Project project, @NotNull VirtualFile file) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

        if (psiFile == null) {
            return false;
        }

        switch (file.getExtension()) {
            case "json":
                return isAcceptableJSON(psiFile);
            case "yaml":
            case "yml":
                return isAcceptableYAML(psiFile);
            default:
                return false;
        }
    }

    @NotNull
    FileEditor createEditor(@NotNull VirtualFile file) {
        Document document = manager.getDocument(file);

        server.attach(file.getPath(), document);

        return new RedocEditor(server, settings, file, document);
    }

    private boolean isAcceptableJSON(@NotNull PsiFile psiFile) {
        PsiElement[] children = psiFile.getChildren();

        for (PsiElement element : children) {
            if (!(element instanceof JsonObjectImpl)) {
                continue;
            }

            JsonProperty schema = ((JsonObjectImpl) element).findProperty("openapi");

            if (schema != null) {
                return Objects.requireNonNull(schema.getValue()).getText().equals("\"3.0.0\"");
            }
        }

        return false;
    }

    private boolean isAcceptableYAML(@NotNull PsiFile psiFile) {
        PsiElement[] children = psiFile.getChildren();

        for (PsiElement element : children) {
            if (!(element instanceof YAMLDocumentImpl)) {
                continue;
            }

            YAMLValue top = ((YAMLDocumentImpl) element).getTopLevelValue();

            for (YAMLPsiElement child : Objects.requireNonNull(top).getYAMLElements()) {
                if (!(child instanceof YAMLKeyValueImpl)) {
                    continue;
                }

                if (!"openapi".equals(child.getName())) {
                    continue;
                }

                YAMLValue value = ((YAMLKeyValueImpl) child).getValue();

                return "3.0.0".equals(value.getText());
            }
        }

        return false;
    }
}
