import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.newsmead.data.StudyConfig
import com.newsmead.fragments.saved.SavedAllFragment
import com.newsmead.fragments.saved.SavedListsFragment
import com.newsmead.fragments.saved.StudyArticlesFragment

public class TabFragmentAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    private val localStudy = StudyConfig.BYPASS_LOGIN

    override fun getItemCount(): Int = if (localStudy) 1 else 2

    // Keep local articles distinct from restored account-backed tab fragments.
    override fun getItemId(position: Int): Long = if (localStudy) 100L else position.toLong()

    override fun containsItem(itemId: Long): Boolean =
        if (localStudy) itemId == 100L else itemId in 0L..1L

    override fun createFragment(position: Int): Fragment {
        if (localStudy) return StudyArticlesFragment()
        return when(position) {
            0 -> SavedListsFragment()
            1 -> SavedAllFragment()
            else -> SavedListsFragment()
        }
    }
}
