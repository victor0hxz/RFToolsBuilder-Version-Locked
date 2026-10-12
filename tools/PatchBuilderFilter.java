import java.io.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class PatchBuilderFilter {
  private static final String TARGET = "mcjty/rftoolsbuilder/modules/builder/blocks/BuilderTileEntity.class";
  private static final String OWNER = "mcjty/rftoolsbuilder/modules/builder/blocks/BuilderTileEntity";
  public static void main(String[] args) throws Exception {
    try (JarFile in = new JarFile(args[0]); JarOutputStream out = new JarOutputStream(new FileOutputStream(args[1]))) {
      var entries = in.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        JarEntry copy = new JarEntry(entry.getName());
        out.putNextEntry(copy);
        if (entry.getName().equals(TARGET)) out.write(patch(in.getInputStream(entry).readAllBytes()));
        else in.getInputStream(entry).transferTo(out);
        out.closeEntry();
      }
    }
  }
  private static byte[] patch(byte[] bytes) {
    ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
    int replacements = 0;
    for (MethodNode method : node.methods) {
      for (AbstractInsnNode i = method.instructions.getFirst(); i != null; ) {
        AbstractInsnNode following = i.getNext();
        if (i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD && f.owner.equals(OWNER) && f.name.equals("filterCache")) {
          AbstractInsnNode next = i.getNext();
          if (next instanceof MethodInsnNode m && m.owner.equals("mcjty/lib/varia/Cached") && m.name.equals("get")) {
            following = next.getNext();
            method.instructions.set(i, new MethodInsnNode(Opcodes.INVOKESPECIAL, OWNER, "getCurrentFilter", "()Ljava/util/function/Predicate;", false));
            method.instructions.remove(next); replacements++;
          }
        }
        i = following;
      }
    }
    if (replacements != 5) throw new IllegalStateException("Expected 5 filter-cache reads, found " + replacements);
    MethodNode getFilter = new MethodNode(Opcodes.ACC_PRIVATE, "getCurrentFilter", "()Ljava/util/function/Predicate;", null, null);
    getFilter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    getFilter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "items", "Lmcjty/lib/container/GenericItemHandler;"));
    getFilter.instructions.add(new InsnNode(Opcodes.ICONST_1));
    getFilter.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "mcjty/lib/container/GenericItemHandler", "getStackInSlot", "(I)Lnet/minecraft/world/item/ItemStack;", false));
    getFilter.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mcjty/rftoolsbase/modules/filter/items/FilterModuleItem", "getCache", "(Lnet/minecraft/world/item/ItemStack;)Ljava/util/function/Predicate;", false));
    getFilter.instructions.add(new InsnNode(Opcodes.ARETURN)); node.methods.add(getFilter);
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
  }
}
