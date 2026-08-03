package net.oktawia.spatialtoolscmp.util;

import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockMatcher {

    private static final class InvalidSyntaxException extends Exception {
        private InvalidSyntaxException(String message) {
            super(message);
        }
    }

    public static Compiled compile(@Nullable String expression) {
        String normalized = expression == null
                ? ""
                : expression.replace("&&", "&").replace("||", "|");

        if (normalized.isBlank()) {
            return Compiled.EMPTY;
        }

        try {
            return new Compiled(toRpn(tokenize(normalized)), true);
        } catch (InvalidSyntaxException e) {
            return Compiled.INVALID;
        }
    }

    public static boolean matches(BlockState state, @Nullable Compiled compiled) {
        if (state == null || compiled == null || !compiled.valid || compiled.rpn.length == 0) {
            return false;
        }

        return evalRpn(compiled.rpn, BlockKeys.of(state.getBlock()));
    }

    public static final class Compiled {

        private final Token[] rpn;
        @Getter
        private final boolean valid;

        private Compiled(Token[] rpn, boolean valid) {
            this.rpn = rpn;
            this.valid = valid;
        }

        public boolean isEmpty() {
            return this.rpn.length == 0;
        }

        public static final Compiled EMPTY = new Compiled(new Token[0], true);
        public static final Compiled INVALID = new Compiled(new Token[0], false);
    }

    private record BlockKeys(String id, Set<String> tags) {

        private static final ThreadLocal<Map<Block, BlockKeys>> CACHE =
                ThreadLocal.withInitial(IdentityHashMap::new);

        static BlockKeys of(Block block) {
            Map<Block, BlockKeys> cache = CACHE.get();
            BlockKeys keys = cache.get(block);

            if (keys != null) {
                return keys;
            }

            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
            Holder<Block> holder = block.builtInRegistryHolder();

            Set<String> tags = holder.tags()
                    .map(tag -> tag.location().toString())
                    .collect(HashSet::new, Set::add, Set::addAll);

            keys = new BlockKeys(blockId == null ? "" : blockId.toString(), tags);
            cache.put(block, keys);

            return keys;
        }
    }

    public static void clearThreadLocalCache() {
        BlockKeys.CACHE.remove();
    }

    private enum TokenType {
        OPERAND, OPERATOR, LPAREN, RPAREN
    }

    private enum Operator {
        NOT("!", 3, true),
        AND("&", 2, false),
        XOR("^", 1, false),
        OR("|", 0, false);

        final String symbol;
        final int precedence;
        final boolean rightAssociative;

        Operator(String symbol, int precedence, boolean rightAssociative) {
            this.symbol = symbol;
            this.precedence = precedence;
            this.rightAssociative = rightAssociative;
        }

        @Nullable
        static Operator fromSymbol(char symbol) {
            for (Operator op : values()) {
                if (op.symbol.charAt(0) == symbol) {
                    return op;
                }
            }

            return null;
        }
    }

    private record Token(TokenType type, String pattern, boolean tag, boolean hasWildcard, Operator op) {

        static Token operand(String raw) {
            boolean tag = raw.startsWith("#");
            String pattern = tag ? raw.substring(1) : raw;

            return new Token(TokenType.OPERAND, pattern, tag, pattern.indexOf('*') >= 0, null);
        }

        static Token op(Operator op) {
            return new Token(TokenType.OPERATOR, null, false, false, op);
        }

        static Token lparen() {
            return new Token(TokenType.LPAREN, null, false, false, null);
        }

        static Token rparen() {
            return new Token(TokenType.RPAREN, null, false, false, null);
        }
    }

    private static List<Token> tokenize(String expression) throws InvalidSyntaxException {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean expectingOperand = true;
        boolean lastIsOperand = false;
        int openParens = 0;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (Character.isWhitespace(c)) {
                continue;
            }

            Operator op = Operator.fromSymbol(c);

            if (c == '(') {
                if (!expectingOperand) {
                    throw new InvalidSyntaxException("Unexpected '(' at position " + i);
                }

                flushOperand(current, tokens);
                tokens.add(Token.lparen());
                openParens++;
                lastIsOperand = false;

            } else if (c == ')') {
                if (expectingOperand && openParens <= 0) {
                    throw new InvalidSyntaxException("Unexpected ')' at position " + i);
                }

                flushOperand(current, tokens);
                tokens.add(Token.rparen());
                expectingOperand = false;
                openParens--;
                lastIsOperand = false;

            } else if (op != null) {
                if (op == Operator.NOT && expectingOperand) {
                    flushOperand(current, tokens);
                    tokens.add(Token.op(op));
                } else if (op != Operator.NOT) {
                    if (lastIsOperand || !expectingOperand) {
                        flushOperand(current, tokens);
                        tokens.add(Token.op(op));
                        expectingOperand = true;
                    }
                } else {
                    throw new InvalidSyntaxException("Unexpected operator '" + c + "' at position " + i);
                }

                lastIsOperand = false;

            } else {
                if (!expectingOperand) {
                    throw new InvalidSyntaxException("Unexpected character '" + c + "' at position " + i);
                }

                current.append(c);
                lastIsOperand = true;
            }
        }

        flushOperand(current, tokens);

        if (tokens.isEmpty()) {
            throw new InvalidSyntaxException("Expression cannot be empty.");
        }

        if (openParens > 0) {
            throw new InvalidSyntaxException("Missing ')' at the end of the expression.");
        }

        Token last = tokens.get(tokens.size() - 1);

        if (expectingOperand && last.type != TokenType.OPERAND && last.type != TokenType.RPAREN) {
            throw new InvalidSyntaxException("Expression ended unexpectedly.");
        }

        return tokens;
    }

    private static void flushOperand(StringBuilder current, List<Token> tokens) {
        if (!current.isEmpty()) {
            tokens.add(Token.operand(current.toString()));
            current.setLength(0);
        }
    }

    private static Token[] toRpn(List<Token> tokens) throws InvalidSyntaxException {
        List<Token> out = new ArrayList<>(tokens.size());
        Deque<Token> stack = new ArrayDeque<>();

        for (Token token : tokens) {
            switch (token.type) {
                case OPERAND -> out.add(token);

                case OPERATOR -> {
                    while (!stack.isEmpty() && stack.peek().type == TokenType.OPERATOR) {
                        Operator current = token.op;
                        Operator top = stack.peek().op;

                        boolean shouldPop = (!current.rightAssociative && current.precedence <= top.precedence)
                                || (current.rightAssociative && current.precedence < top.precedence);

                        if (!shouldPop) {
                            break;
                        }

                        out.add(stack.pop());
                    }

                    stack.push(token);
                }

                case LPAREN -> stack.push(token);

                case RPAREN -> {
                    boolean found = false;

                    while (!stack.isEmpty()) {
                        Token top = stack.peek();

                        if (top.type == TokenType.LPAREN) {
                            stack.pop();
                            found = true;
                            break;
                        }

                        out.add(stack.pop());
                    }

                    if (!found) {
                        throw new InvalidSyntaxException("Mismatched parentheses.");
                    }
                }
            }
        }

        while (!stack.isEmpty()) {
            Token top = stack.pop();

            if (top.type == TokenType.LPAREN) {
                throw new InvalidSyntaxException("Mismatched parentheses.");
            }

            out.add(top);
        }

        Token[] rpn = out.toArray(Token[]::new);
        validateRpnStackDepth(rpn);

        return rpn;
    }

    private static void validateRpnStackDepth(Token[] rpn) throws InvalidSyntaxException {
        int depth = 0;

        for (Token token : rpn) {
            switch (token.type) {
                case OPERAND -> depth++;
                case OPERATOR -> {
                    depth -= token.op == Operator.NOT ? 1 : 2;

                    if (depth < 0) {
                        throw new InvalidSyntaxException("Unexpected operator " + token.op);
                    }

                    depth++;
                }
                case LPAREN, RPAREN -> throw new InvalidSyntaxException("Unexpected token: " + token.type);
            }
        }

        if (depth != 1) {
            throw new InvalidSyntaxException("Depth at the end should equal 1");
        }
    }

    // Unguarded stack access is safe only because toRpn ran validateRpnStackDepth, which proves
    // every operator has its operands and exactly one value is left at the end.
    private static boolean evalRpn(Token[] rpn, BlockKeys keys) {
        boolean[] stack = new boolean[rpn.length];
        int sp = 0;

        for (Token token : rpn) {
            if (token.type == TokenType.OPERAND) {
                stack[sp++] = matchesOperand(token, keys);
                continue;
            }

            if (token.op == Operator.NOT) {
                stack[sp - 1] = !stack[sp - 1];
                continue;
            }

            boolean right = stack[--sp];
            boolean left = stack[--sp];

            stack[sp++] = switch (token.op) {
                case AND -> left && right;
                case OR -> left || right;
                case XOR -> left ^ right;
                case NOT -> throw new IllegalStateException("NOT is handled above");
            };
        }

        return stack[0];
    }

    private static boolean matchesOperand(Token token, BlockKeys keys) {
        if (token.tag) {
            return token.hasWildcard
                    ? matchesAnyGlob(token.pattern, keys.tags())
                    : keys.tags().contains(token.pattern);
        }

        return token.hasWildcard
                ? globMatchStarOnly(token.pattern, keys.id())
                : token.pattern.equals(keys.id());
    }

    private static boolean matchesAnyGlob(String pattern, Set<String> values) {
        if ("*".equals(pattern)) {
            return !values.isEmpty();
        }

        for (String value : values) {
            if (globMatchStarOnly(pattern, value)) {
                return true;
            }
        }

        return false;
    }

    private static boolean globMatchStarOnly(String pattern, String text) {
        int p = 0;
        int t = 0;
        int star = -1;
        int match = 0;

        while (t < text.length()) {
            if (p < pattern.length() && pattern.charAt(p) == text.charAt(t)) {
                p++;
                t++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                match = t;
            } else if (star != -1) {
                p = star + 1;
                t = ++match;
            } else {
                return false;
            }
        }

        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }

        return p == pattern.length();
    }

    private BlockMatcher() {
    }
}
