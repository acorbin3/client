package org.runestar.client.updater.mapper.std.classes

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.runestar.client.updater.mapper.*
import org.runestar.client.updater.mapper.and
import org.runestar.client.updater.mapper.predicateOf

@DependsOn(Node::class)
class PlatformInfo : IdentityMapper.Class() {

    override val predicate = predicateOf<Class2> { it.superType == type<Node>() }
            .and { it.instanceFields.count { it.type == Type.BOOLEAN_TYPE } == 2 }
            .and { it.instanceFields.count { it.type == Type.INT_TYPE } >= 15  }

    @MethodParameters("packet")
    class write : IdentityMapper.InstanceMethod() {
        override val predicate = predicateOf<Method2> { it.arguments.size == 1 }
    }

    @MethodParameters()
    class size : IdentityMapper.InstanceMethod() {
        override val predicate = predicateOf<Method2> { it.arguments.isEmpty() }
    }

    class osType : OrderMapper.InConstructor.Field(PlatformInfo::class, 0) {
        override val predicate = predicateOf<Instruction2> { it.opcode == Opcodes.PUTFIELD && it.fieldType == Type.INT_TYPE }
    }

    class os64Bit : OrderMapper.InConstructor.Field(PlatformInfo::class, 0) {
        override val predicate = predicateOf<Instruction2> { it.opcode == Opcodes.PUTFIELD && it.fieldType == Type.BOOLEAN_TYPE }
    }
    class osVersion : OrderMapper.InConstructor.Field(PlatformInfo::class, 1) {
        override val predicate = predicateOf<Instruction2> { it.opcode == Opcodes.PUTFIELD && it.fieldType == Type.INT_TYPE }
    }
    class javaVendorType : OrderMapper.InConstructor.Field(PlatformInfo::class, 2) {
        override val predicate = predicateOf<Instruction2> { it.opcode == Opcodes.PUTFIELD && it.fieldType == Type.INT_TYPE }
    }
    class javaVersionMajor : OrderMapper.InConstructor.Field(PlatformInfo::class, 3) {
        override val predicate = predicateOf<Instruction2> { it.opcode == Opcodes.PUTFIELD && it.fieldType == Type.INT_TYPE }
    }
    class javaVersionMinor : OrderMapper.InConstructor.Field(PlatformInfo::class, 4) {
        override val predicate = predicateOf<Instruction2> { it.opcode == Opcodes.PUTFIELD && it.fieldType == Type.INT_TYPE }
    }
    class javaVersionPatch : OrderMapper.InConstructor.Field(PlatformInfo::class, 5) {
        override val predicate = predicateOf<Instruction2> { it.opcode == Opcodes.PUTFIELD && it.fieldType == Type.INT_TYPE }
    }
    class maxMemoryMB : OrderMapper.InConstructor.Field(PlatformInfo::class, 6) {
        override val predicate = predicateOf<Instruction2> { it.opcode == Opcodes.PUTFIELD && it.fieldType == Type.INT_TYPE }
    }
    class cpuCores : OrderMapper.InConstructor.Field(PlatformInfo::class, 7) {
        override val predicate = predicateOf<Instruction2> { it.opcode == Opcodes.PUTFIELD && it.fieldType == Type.INT_TYPE }
    }



}