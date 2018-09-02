package au.com.grieve.multibridge.builder.Vanilla.patcher;

import org.objectweb.asm.*;
import com.mojang.authlib.GameProfile;

/**
 * LoginListener
 * Source: https://github.com/ME1312/VanillaCord/blob/1.12/src/main/java/uk/co/thinkofdeath/vanillacord/LoginListener.java
 */
public class LoginListener extends ClassVisitor {
    private final String networkManager;
    private String fieldName;
    private String fieldDesc;
    private String thisName;

    public LoginListener(ClassWriter classWriter, String networkManager) {
        super(Opcodes.ASM5, classWriter);
        this.networkManager = "L" + networkManager + ";";
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        thisName = name;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if (desc.equals(networkManager)) {
            fieldName = name;
            fieldDesc = desc;
        }
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        Type methodArgs = Type.getMethodType(desc);
        if (methodArgs.getArgumentTypes().length == 1
                && methodArgs.getArgumentTypes()[0].equals(Type.getType(GameProfile.class))
                && methodArgs.getReturnType().equals(Type.getType(GameProfile.class))) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            mv.visitCode();
            mv.visitLabel(new Label());
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, thisName, fieldName, fieldDesc);
            mv.visitVarInsn(Opcodes.ASTORE, 2);

            mv.visitLabel(new Label());
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "uk/co/thinkofdeath/vanillacord/util/BungeeHelper",
                    "injectProfile",
                    "(Ljava/lang/Object;Lcom/mojang/authlib/GameProfile;)Lcom/mojang/authlib/GameProfile;",
                    false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
            return null;
        }
        return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, name, desc, signature, exceptions)) {

            private int state = 0;

            @Override
            public void visitLdcInsn(Object cst) {
                super.visitLdcInsn(cst);
                if (cst.equals("Unexpected hello packet")) {
                    setState(0, 1);
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == Opcodes.INVOKEVIRTUAL) {
                    if (desc.contains("GameProfile") && state == 1) {
                        setState(1, 2);
                    }
                }
                if (state == 4) {
                    setState(4, 5);
                    mv.visitIntInsn(Opcodes.BIPUSH, 0);
                    return;
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                if (state == 3) {
                    setState(3, 4);
                    return;
                }
                super.visitFieldInsn(opcode, owner, name, desc);
            }

            @Override
            public void visitVarInsn(int opcode, int var) {
                if (state == 2 && opcode == Opcodes.ALOAD) {
                    setState(2, 3);
                    return;
                }
                super.visitVarInsn(opcode, var);
            }

            private void setState(int old, int n) {
                if (state != old) {
                    throw new RuntimeException("Inject failed");
                }
                state = n;
            }
        };
    }
}