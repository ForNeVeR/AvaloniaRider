using Avalonia;
using Avalonia.Controls;
using Avalonia.Markup.Xaml;

namespace ClassLibrary1
{
    public class MyControl : UserControl
    {
        public MyControl()
        {
            InitializeComponent();
        }

        private void InitializeComponent()
        {
            AvaloniaXamlLoader.Load(this);
        }
    }
}