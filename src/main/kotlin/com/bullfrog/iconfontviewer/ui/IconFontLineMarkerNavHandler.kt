package com.bullfrog.iconfontviewer.ui

import com.android.tools.adtui.LightCalloutPopup
import com.bullfrog.iconfontviewer.model.IconFontPopupModel
import com.bullfrog.iconfontviewer.util.*
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent


class IconFontLineMarkerNavHandler(
    private val smartPointer: SmartPsiElementPointer<PsiElement>,
    private val matchedFont: java.awt.Font,
    private val fontPath: String
) : GutterIconNavigationHandler<PsiElement> {

    private lateinit var searchTextField: SearchTextField

    private val speedSearch = SpeedSearch().apply {
        setEnabled(true)
    }

    override fun navigate(e: MouseEvent?, elt: PsiElement?) {
        val element = smartPointer.element ?: return

        val iconList = buildIconListForFont(matchedFont, fontPath, element)
        if (iconList.isEmpty()) return

        val collectionModel = CollectionListModel(iconList)
        val filteringModel = NameFilteringListModel<IconFontPopupModel>(
            collectionModel,
            { it.key },
            speedSearch::shouldBeShowing,
            { StringUtil.notNullize(speedSearch.filter) }
        )

        speedSearch.addChangeListener {
            filteringModel.refilter()
        }

        val popup = LightCalloutPopup(null, null, null)
        popup.show(
            buildPopupPanel(filteringModel, popup),
            null,
            MouseInfo.getPointerInfo().location,
            Balloon.Position.below
        )
    }

    private fun buildPopupPanel(
        listModel: NameFilteringListModel<IconFontPopupModel>,
        popup: LightCalloutPopup
    ): JPanel {
        return JPanel().apply {
            preferredSize = Dimension(300.jbScale(), 450.jbScale())
            layout = BorderLayout()
            add(
                SearchTextField().apply {
                    searchTextField = this
                    isFocusable = true
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.Popup.separatorColor()),
                        JBEmptyBorder(8, 8, 8, 8)
                    )
                    addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            speedSearch.updatePattern(e.document.getText(0, e.document.length))
                        }
                    })
                },
                BorderLayout.NORTH
            )
            add(
                JScrollPane().apply {
                    preferredSize = Dimension(300.jbScale(), 400.jbScale())
                    viewport.view = JList<IconFontPopupModel>().apply {
                        border = JBUI.Borders.empty(4.jbScale(), 8.jbScale())
                        model = listModel
                        cellRenderer = PopupListCellRenderer()
                        // 用 MouseListener 替代 ListSelectionListener，避免选择变化时误触发替换
                        addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                val idx = locationToIndex(e.point)
                                if (idx < 0) return
                                val selected = listModel.getElementAt(idx) ?: return
                                doReplace(selected)
                                popup.close()
                            }
                        })
                    }
                    border = BorderFactory.createEmptyBorder()
                },
                BorderLayout.CENTER
            )
            isVisible = true
            isFocusCycleRoot = true
            isFocusTraversalPolicyProvider = true
            focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
                override fun getDefaultComponent(aContainer: Container?): Component {
                    return searchTextField
                }
            }
            addPropertyChangeListener("ancestor") {
                searchTextField.requestFocusInWindow()
            }
        }
    }

    class PopupListCellRenderer : ListCellRenderer<IconFontPopupModel> {
        override fun getListCellRendererComponent(
            list: JList<out IconFontPopupModel>?,
            value: IconFontPopupModel?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            return JPanel().apply {
                border = JBUI.Borders.empty(10.jbScale())
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                background = if (isSelected) list?.selectionBackground else list?.background
                add(JLabel().apply {
                    icon = value?.icon
                })
                add(JLabel().apply {
                    border = JBUI.Borders.emptyLeft(8.jbScale())
                    text = value?.key ?: ""
                })
            }
        }
    }

    private fun doReplace(model: IconFontPopupModel) {
        val element = smartPointer.element ?: return
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(
                element.project,
                "Replace IconFont",
                null,
                {
                    when {
                        element.isValidExpression() -> {
                            val expression = if (element is PsiReferenceExpression) {
                                PsiElementFactory.getInstance(element.project)
                                    .createExpressionFromText(R_PREFIX + model.key, element)
                            } else {
                                KtPsiFactory(element.project).createExpression(R_PREFIX + model.key)
                            }
                            element.replace(expression)
                        }
                        element.isValidLayoutXmlElement() -> {
                            val parent = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java)
                            parent?.setValue(XML_PREFIX + model.key)
                        }
                        element.isStringResourceTagName() -> {
                            // strings.xml: 从 token 向上找到父 XmlTag，替换文本内容
                            val xmlTag = element.getParentStringResourceTag() ?: return@runWriteCommandAction
                            xmlTag.value.setText(model.text)
                        }
                        element.isValidResXmlToken() -> {
                            val xmlTagValue = (element as? XmlTag)?.value
                            xmlTagValue?.setText(model.text)
                        }
                    }
                }
            )
        }
    }
}
