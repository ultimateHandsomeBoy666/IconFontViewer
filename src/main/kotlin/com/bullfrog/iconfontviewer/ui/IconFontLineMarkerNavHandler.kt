package com.bullfrog.iconfontviewer.ui

import com.android.tools.adtui.LightCalloutPopup
import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.model.IconFontPopupModel
import com.bullfrog.iconfontviewer.util.*
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceExpression
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
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent


class IconFontLineMarkerNavHandler : GutterIconNavigationHandler<PsiElement> {

    private lateinit var searchTextField: SearchTextField

    private var psiElement: PsiElement? = null //TODO 这里会不会有内存泄漏？

    private val speedSearch = SpeedSearch().apply {
        setEnabled(true)
        addChangeListener {
            iconFontListModel?.refilter()
        }
    }

    private var iconFontListModel: NameFilteringListModel<IconFontPopupModel>? = null

    fun setPsiElement(psiElement: PsiElement?) {
        if (psiElement == null) {
            return
        }
        if (PsiManager.getInstance(psiElement.project).areElementsEquivalent(psiElement, this.psiElement)) {
            return
        }
        this.psiElement = psiElement
        iconFontListModel = NameFilteringListModel<IconFontPopupModel>(
            CollectionListModel(IconFontSettings.getInstance(psiElement.project).iconPopupList),
            { it.key },
            speedSearch::shouldBeShowing,
            { StringUtil.notNullize(speedSearch.filter) }
        )
    }

    override fun navigate(e: MouseEvent?, elt: PsiElement?) {
        val popup = LightCalloutPopup(null, null, null)
        popup.show(buildPopupPanel(), null, MouseInfo.getPointerInfo().location, Balloon.Position.below)
    }

    private fun buildPopupPanel(): JPanel {
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
                        model = iconFontListModel
                        cellRenderer = PopupListCellRenderer()
                        addListSelectionListener {
                            if (it.valueIsAdjusting) return@addListSelectionListener
                            updatePsiElement(selectedValue)
                        }
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

    private fun updatePsiElement(popupModel: IconFontPopupModel?) {
        val model = popupModel ?: return
        val element = psiElement ?: return
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(
                element.project,
                "replace",
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
                            psiElement = element.replace(expression)
                        }
                        element.isValidLayoutXmlElement() -> {
                            val parent = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java)
                            parent?.setValue(XML_PREFIX + model.key)
                            psiElement = parent?.valueElement ?: element
                        }
                        element.isValidResXmlToken() -> {
                            val xmlTagValue = (element as? XmlTag)?.value
                            xmlTagValue?.setText(XML_PREFIX + model.key)
                        }
                    }
                }
            )
        }
    }

}