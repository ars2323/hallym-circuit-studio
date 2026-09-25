/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.palette;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 왼쪽 부품 트리 위 검색창(명세 F, 검토 반영 1). 명령 팔레트와 같은 검색 모델({@link Palette#search})을 쓰고,
 * 입력하는 동안 트리 자리에 걸러진 부품·서브회로 목록을 보인다(명령은 빼고). 고르면 원조처럼 그 부품의 도구를
 * 고른다: 캔버스를 누르면 놓인다. 뒤 숫자(예: {@code mux 32})는 도구 속성에 넣는다.
 */
public final class ToolboxSearch extends JPanel {
    private static final long serialVersionUID = 1L;

    private final Project proj;
    private final JTextField field = new JTextField();
    private final DefaultListModel<Palette.Item> model = new DefaultListModel<>();
    private final JList<Palette.Item> list = new JList<>(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);

    public ToolboxSearch(Project proj, JComponent tree) {
        super(new BorderLayout());
        this.proj = proj;
        field.putClientProperty("JTextField.placeholderText", Messages.get("toolbox.search"));
        field.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
        field.setToolTipText(Messages.get("toolbox.searchTip"));
        field.getAccessibleContext().setAccessibleName(Messages.get("toolbox.search"));
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(Tokens.SPACE_1, Tokens.SPACE_1, Tokens.SPACE_1, Tokens.SPACE_1));
        top.add(field, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        list.setCellRenderer((l, it, i, sel, focus) -> {
            String attrs = Palette.attrText(it);
            JLabel lab = new JLabel("<html>" + esc(Palette.displayName(it)) + (attrs.isEmpty() ? ""
                    : " <span style='color:#" + hex(Tokens.TEXT_2) + "'>" + esc(attrs) + "</span>") + "</html>");
            lab.setOpaque(true);
            lab.setBackground(sel ? Tokens.BLUE_TINT_2 : Tokens.WHITE);
            lab.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return lab;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (list.getSelectedValue() != null) {
                    choose(list.getSelectedValue());
                }
            }
        });
        body.add(tree, "tree");
        body.add(new JScrollPane(list), "list");
        add(body, BorderLayout.CENTER);
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    field.setText("");
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN && !model.isEmpty()) {
                    list.requestFocusInWindow();
                    list.setSelectedIndex(0);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && !model.isEmpty()) {
                    choose(list.getSelectedIndex() >= 0 ? list.getSelectedValue() : model.get(0));
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && list.getSelectedValue() != null) {
                    choose(list.getSelectedValue());
                }
            }
        });
    }

    /** 검색창(테스트·스크린샷용). */
    public JTextField field() {
        return field;
    }

    private void refresh() {
        String q = field.getText().trim();
        model.clear();
        if (q.isEmpty()) {
            cards.show(body, "tree");
            return;
        }
        for (Palette.Item it : results(q, proj.getLogisimFile())) {
            model.addElement(it);
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        cards.show(body, "list");
    }

    /** 트리를 거른 결과: 팔레트 검색에서 명령을 뺀 부품·서브회로. */
    public static List<Palette.Item> results(String query, LogisimFile file) {
        List<Library> libs = new ArrayList<>(file.getLibraries());
        List<Palette.Item> ret = new ArrayList<>();
        for (Palette.Item it : Palette.search(query, libs, file.getCircuits(), PaletteActions.recent(),
                PaletteActions.favorites())) {
            if (it.kind != Palette.Kind.COMMAND) {
                ret.add(it);
            }
        }
        return ret;
    }

    /** 고른 결과의 도구: 부품은 그 라이브러리의 원조 도구, 서브회로는 파일의 서브회로 도구. */
    public static Tool toolFor(LogisimFile file, Palette.Item it) {
        if (it.kind == Palette.Kind.COMPONENT && it.library != null) {
            return it.library.getTool(it.name);
        }
        if (it.kind == Palette.Kind.SUBCIRCUIT) {
            Circuit c = it.circuit;
            for (Tool t : file.getTools()) {
                if (t instanceof AddTool && ((AddTool) t).getFactory() == c.getSubcircuitFactory()) {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * 뒤 숫자(예: {@code mux 32})를 도구 속성으로: 원조 {@link com.cburch.logisim.gui.main.ToolAttributeAction}.
     * 원조는 도구 기본값과 다른 속성을 .circ의 {@code <lib>} 안 {@code <tool>}에 저장하므로, 속성 표에서 바꿀 때와
     * 같은 동작이어야 되돌리기와 "바뀜" 표시가 맞는다. 이미 같은 값이면 동작을 만들지 않는다.
     */
    static List<com.cburch.logisim.proj.Action> attributeActions(Tool t, Palette.Item it) {
        List<com.cburch.logisim.proj.Action> ret = new ArrayList<>();
        AttributeSet as = t.getAttributeSet();
        if (as == null) {
            return ret;
        }
        for (Map.Entry<String, String> e : it.attrs.entrySet()) {
            @SuppressWarnings("unchecked")
            Attribute<Object> a = (Attribute<Object>) as.getAttribute(e.getKey());
            if (a == null) {
                continue;
            }
            Object v = a.parse(e.getValue());
            if (!v.equals(as.getValue(a))) {
                ret.add(com.cburch.logisim.gui.main.ToolAttributeAction.create(t, a, v));
            }
        }
        return ret;
    }

    private void choose(Palette.Item it) {
        Tool t = toolFor(proj.getLogisimFile(), it);
        if (t == null) {
            return;
        }
        for (com.cburch.logisim.proj.Action act : attributeActions(t, it)) {
            proj.doAction(act); // 원조 속성 표로 도구 속성을 바꿀 때와 같은 동작(되돌리기, 파일 바뀜 표시)
        }
        proj.setTool(t);
        if (it.kind == Palette.Kind.COMPONENT) {
            PaletteActions.remember(it.name);
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String hex(java.awt.Color c) {
        return String.format("%06X", c.getRGB() & 0xFFFFFF);
    }
}
